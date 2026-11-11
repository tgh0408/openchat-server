package com.openchat.user.service;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageUpload;
import com.openchat.common.exception.BusinessException;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.service.MessageResourceService;
import com.openchat.chat.service.MessageUploadService;
import com.openchat.common.domain.enums.VideoFormat;
import com.openchat.common.utils.JsonUtils;
import com.openchat.common.utils.RedisUtils;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.MultipartClient;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import com.openchat.oss.OssExceptionDiagnostics;
import com.openchat.user.domain.vo.oss.*;
import com.openchat.common.service.MinioBucketService;
import io.minio.errors.ErrorResponseException;
import io.minio.Http.Method;
import io.minio.messages.Part;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Redis 保存账号隔离的七天续传会话，MinIO 作为分片和最终状态的事实来源。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultipartUploadService {
    public static final long MAX_BYTES = 6L * 1024 * 1024 * 1024;
    public static final int PART_SIZE = 16 * 1024 * 1024;
    private static final Duration RETENTION = Duration.ofDays(7);
    private final MinIOTemplate minio;
    private final MultipartClient multipart;
    private final OssProperties properties;
    private final RedissonClient redisson;
    private final MessageResourceService messageResources;
    private final MessageUploadService messageUploads;
    private final MinioBucketService minioBucketService;

    @Data
    public static class Upload {
        private Long uid;
        private String bucket;
        private String objectKey;
        private String uploadId;
        private String sha256;
        private long fileSize;
        private MessageTypeEnum targetType;
        private String contentType;
        private Long roomId;
        private String clientMsgId;
    }

    @Transactional(rollbackFor = Exception.class)
    public MultipartUploadResp initiate(Long uid, MultipartUploadInitReq req) {
        requireUid(uid);
        String bucket = minioBucketService.currentBucket();
        Message message = messageUploads.requireUploadMessage(uid, req.getRoomId(), req.getClientMsgId());
        MessageTypeEnum targetTypeEnum = Optional.ofNullable(req.getTargetType()).orElse(MessageTypeEnum.VIDEO);
        if ((targetTypeEnum != MessageTypeEnum.VIDEO
                && targetTypeEnum != MessageTypeEnum.FILE) || req.getFileName() == null
                || req.getFileName().isBlank() || req.getFileName().length() > 255
                || req.getFileName().matches(".*[/\\\\\\p{Cntrl}].*"))
            throw new BusinessException("文件类型或文件名无效");
        VideoFormat format = targetTypeEnum == MessageTypeEnum.VIDEO
                ? VideoFormat.fromName(req.getFileName()) : null;
        String contentType = format == null ? "application/octet-stream" : format.getContentType();
        long maximum = targetTypeEnum == MessageTypeEnum.VIDEO
                ? 2L * 1024 * 1024 * 1024 : MAX_BYTES;
        if (req.getFileSize() == null || req.getFileSize() < (
                targetTypeEnum == MessageTypeEnum.VIDEO ? 12 : 1)
                || req.getFileSize() > maximum
                || req.getSha256() == null || !req.getSha256().matches("[a-f0-9]{64}")) {
            throw new BusinessException(targetTypeEnum == MessageTypeEnum.VIDEO
                    ? "请选择不超过 2 GiB 的有效视频" : "请选择不超过 6 GiB 的有效文件");
        }
        messageUploads.validateFile(message, targetTypeEnum, req.getFileName(), req.getFileSize());
        // 按占位隔离续传，管理员终止一条消息不能影响其他房间的同名文件。
        String sessionKey = digest(req.getRoomId() + ":" + req.getClientMsgId() + ":" + (
                targetTypeEnum == MessageTypeEnum.FILE ? "file:" : "") + req.getSha256()
                + ":" + req.getFileSize() + ":" + req.getFileName());
        MessageUpload binding = messageUploads.binding(message, sessionKey);
        return locked("initiate", uid, sessionKey, () -> {
            String key = key(uid, sessionKey);
            String saved = RedisUtils.getRaw(key);
            Upload upload = saved == null ? null : JsonUtils.toObj(saved, Upload.class);
            if (upload == null && binding != null && binding.getUploadId() != null) {
                upload = new Upload();
                upload.setUid(uid);
                upload.setBucket(bucket);
                upload.setRoomId(req.getRoomId());
                upload.setClientMsgId(req.getClientMsgId());
                upload.setObjectKey(messageResources.objectName(uid, binding.getObjectPath()));
                upload.setUploadId(binding.getUploadId());
                upload.setSha256(req.getSha256());
                upload.setFileSize(req.getFileSize());
                upload.setTargetType(targetTypeEnum);
                upload.setContentType(contentType);
            }
            if (upload != null) {
                requireOwner(uid, upload);
                normalize(upload);
            }
            if (upload != null) {
                try {
                    MultipartUploadResp response = status(sessionKey, upload);
                    if (!compareChange(key, saved, JsonUtils.toStr(upload), RETENTION.toMillis()))
                        throw new BusinessException("上传会话已变化，请重试");
                    messageUploads.bind(message, sessionKey, upload.getUploadId(),
                            minioBucketService.path(bucket, upload.getObjectKey()));
                    return response;
                } catch (Exception error) {
                    if (!isMissing(error, "NoSuchUpload")) throw error;
                    // 仅明确不存在才重建；断网/5xx 不丢弃仍可续传的会话。
                }
            }
            Upload created = new Upload();
            created.setUid(uid);
            created.setBucket(bucket);
            created.setRoomId(req.getRoomId());
            created.setClientMsgId(req.getClientMsgId());
            created.setSha256(req.getSha256());
            created.setFileSize(req.getFileSize());
            created.setTargetType(targetTypeEnum);
            created.setContentType(contentType);
            String path = targetTypeEnum == MessageTypeEnum.VIDEO ? "video/" : "file/";
            created.setObjectKey(path + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    + "/" + uid + "/" + UUID.randomUUID() + "."
                    + (targetTypeEnum == MessageTypeEnum.VIDEO
                    ? extension(req.getFileName()) : "bin"));
            created.setUploadId(multipart.initiate(bucket, properties.getRegion(),
                    created.getObjectKey(), Map.of("openchat-sha256", created.getSha256(),
                            "openchat-target-type", String.valueOf(targetTypeEnum.getType())), contentType));
            try {
                if (!compareChange(key, saved, JsonUtils.toStr(created), RETENTION.toMillis()))
                    throw new BusinessException("上传会话已变化，请重试");
            } catch (RuntimeException error) {
                try {
                    multipart.abort(bucket, properties.getRegion(),
                            created.getObjectKey(), created.getUploadId());
                } catch (Exception cleanupError) {
                    // 补偿失败不能覆盖会话发布失败；两者一起交给异常边界记录。
                    if (cleanupError != error) error.addSuppressed(cleanupError);
                }
                throw error;
            }
            messageUploads.bind(message, sessionKey, created.getUploadId(),
                    minioBucketService.path(bucket, created.getObjectKey()));
            return response(sessionKey, created, false, List.of());
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> partUrl(Long uid, MultipartUploadPartReq req) {
        messageResources.requireOwner(uid);
        return locked("part-url", uid, req.getSessionKey(), () -> {
            Upload upload = load(uid, req.getSessionKey(), req.getUploadId());
            int count = (int) ((upload.getFileSize() + PART_SIZE - 1) / PART_SIZE);
            if (req.getPartNumber() == null || req.getPartNumber() < 1 || req.getPartNumber() > count)
                throw new BusinessException("文件分片编号无效");
            return Map.of("uploadUrl", minio.getPresignedMultipartUrl(upload.getObjectKey(), Method.PUT,
                    Map.of("uploadId", upload.getUploadId(), "partNumber", req.getPartNumber().toString()),
                    upload.getBucket()));
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public MultipartUploadResp complete(Long uid, MultipartUploadCompleteReq req) {
        messageResources.requireOwner(uid);
        return locked("complete", uid, req.getSessionKey(), () -> {
            Upload upload = load(uid, req.getSessionKey(), req.getUploadId());
            if (!completed(upload)) {
                List<Part> parts = checkedParts(upload);
                if (parts.size() != (upload.getFileSize() + PART_SIZE - 1) / PART_SIZE)
                    throw new BusinessException("文件分片尚未全部上传，请继续发送");
                multipart.complete(upload.getBucket(), properties.getRegion(), upload.getObjectKey(),
                        upload.getUploadId(), parts);
                if (!completed(upload)) throw new BusinessException("视频合并尚未确认，请重试");
            }
            return response(req.getSessionKey(), upload, true, List.of());
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void abort(Long uid, MultipartUploadCompleteReq req) {
        messageResources.requireOwner(uid);
        locked("abort", uid, req.getSessionKey(), () -> {
            Upload upload = load(uid, req.getSessionKey(), req.getUploadId());
            multipart.abort(upload.getBucket(), properties.getRegion(),
                    upload.getObjectKey(), upload.getUploadId());
            String key = key(uid, req.getSessionKey());
            String saved = RedisUtils.getRaw(key);
            if (saved != null && req.getUploadId().equals(JsonUtils.toObj(saved, Upload.class).getUploadId()))
                compareChange(key, saved, "", 0);
            return null;
        });
    }

    private MultipartUploadResp status(String sessionKey, Upload upload) {
        if (completed(upload)) return response(sessionKey, upload, true, List.of());
        return response(sessionKey, upload, false, checkedParts(upload));
    }

    /** 合并必须使用存储端返回的 ETag、顺序和长度，不接受客户端伪造的完成列表。 */
    private List<Part> checkedParts(Upload upload) {
        List<Part> parts = new ArrayList<>(multipart.parts(upload.getBucket(), properties.getRegion(),
                upload.getObjectKey(), upload.getUploadId()));
        parts.sort(Comparator.comparingInt(Part::partNumber));
        Set<Integer> numbers = new HashSet<>();
        for (Part part : parts) {
            long start = (part.partNumber() - 1L) * PART_SIZE;
            if (part.partNumber() < 1 || start >= upload.getFileSize()
                    || !numbers.add(part.partNumber())
                    || part.partSize() != Math.min(PART_SIZE, upload.getFileSize() - start)) {
                throw new BusinessException("存储端视频分片大小或编号不匹配");
            }
        }
        return parts;
    }

    private boolean completed(Upload upload) {
        try {
            var object = minio.statObject(upload.getBucket(), upload.getObjectKey());
            if (object.size() != upload.getFileSize()
                    || !upload.getContentType().equalsIgnoreCase(object.contentType())
                    || !Set.of(upload.getSha256()).equals(object.userMetadata().get("openchat-sha256"))) {
                throw new BusinessException("已上传文件大小、类型或内容标记不匹配");
            }
            MessageTypeEnum targetType = upload.getTargetType();
            if (targetType == MessageTypeEnum.FILE
                    && !Set.of(String.valueOf(MessageTypeEnum.FILE.getType())).equals(
                    object.userMetadata().get("openchat-target-type")))
                throw new BusinessException("已上传文件来源标记不匹配");
            if (targetType == MessageTypeEnum.VIDEO) {
                byte[] header = minio.getObjectHeader(upload.getBucket(), upload.getObjectKey(), 32);
                VideoFormat format = VideoFormat.fromName(upload.getObjectKey()
                        .substring(upload.getObjectKey().lastIndexOf('/') + 1));
                if (!format.matches(header)) {
                    throw new BusinessException("已上传视频内容与扩展名不匹配");
                }
            }
            return true;
        } catch (Exception error) {
            if (isMissing(error, "NoSuchKey") || isMissing(error, "NoSuchObject")) return false;
            throw error;
        }
    }

    private MultipartUploadResp response(String key, Upload upload, boolean done, List<Part> parts) {
        return new MultipartUploadResp(key, upload.getUploadId(),
                OssUrlUtil.toPublicUrl(properties.getPublicEndpoint(),
                        minioBucketService.path(upload.getBucket(), upload.getObjectKey())),
                upload.getFileSize(), PART_SIZE, done,
                parts.stream().map(p -> new MultipartUploadResp.UploadedPart(
                        p.partNumber(), p.etag(), p.partSize())).collect(Collectors.toList()));
    }

    private Upload load(Long uid, String sessionKey, String uploadId) {
        if (uploadId == null || uploadId.isBlank() || uploadId.length() > 512)
            throw new BusinessException("视频上传会话无效");
        String saved = RedisUtils.getRaw(key(uid, sessionKey));
        if (saved == null) throw new BusinessException("视频上传会话已过期，请重新发送");
        Upload upload = JsonUtils.toObj(saved, Upload.class);
        requireOwner(uid, upload);
        normalize(upload);
        if (!uploadId.equals(upload.getUploadId())) throw new BusinessException("视频上传会话不匹配");
        var message = messageUploads.requireUploadMessage(uid, upload.getRoomId(), upload.getClientMsgId());
        messageUploads.requireBinding(message, sessionKey, uploadId,
                minioBucketService.path(upload.getBucket(), upload.getObjectKey()));
        return upload;
    }

    private void requireOwner(Long uid, Upload upload) {
        if (!uid.equals(upload.getUid())) throw new BusinessException("无权访问视频上传会话");
    }

    private void requireUid(Long uid) {
        if (uid == null || uid <= 0) throw new BusinessException("请先登录");
    }

    private String key(Long uid, String sessionKey) {
        requireUid(uid);
        if (sessionKey == null || !sessionKey.matches("[a-f0-9]{64}"))
            throw new BusinessException("视频上传会话无效");
        return "openchat:video-upload:" + uid + ":" + sessionKey;
    }

    /** 同一账号同一文件串行控制操作；锁续租避免慢速 MinIO 调用穿透固定租期。 */
    private <T> T locked(String operation, Long uid, String sessionKey, Supplier<T> action) {
        String lockKey = key(uid, sessionKey) + ":lock";
        RLock lock = null;
        boolean acquired = false;
        Exception failure = null;
        try {
            lock = redisson.getLock(lockKey);
            acquired = lock.tryLock();
            if (!acquired) throw new BusinessException("视频上传正在处理中，请重试");
            return action.get();
        } catch (BusinessException error) {
            failure = error;
            if (error.getCause() != null || error.getSuppressed().length > 0) {
                logFailure(operation, uid, sessionKey, error);
            }
            throw error;
        } catch (Exception error) {
            failure = error;
            logFailure(operation, uid, sessionKey, error);
            throw storageFailure(error);
        } finally {
            if (acquired) {
                try {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                } catch (Exception releaseError) {
                    logFailure(operation + ".unlock", uid, sessionKey, releaseError);
                    if (failure == null) throw storageFailure(releaseError);
                    if (releaseError != failure) failure.addSuppressed(releaseError);
                }
            }
        }
    }

    /** 日志记录脱敏副本；原始异常继续保留在内存中的 cause 链，不进入对外响应。 */
    private void logFailure(String operation, Long uid, String sessionKey, Exception error) {
        log.error("Multipart upload failed: operation={}, uid={}, sessionKey={}",
                operation, uid, sessionKey, OssExceptionDiagnostics.sanitized(error));
    }

    private BusinessException storageFailure(Exception cause) {
        BusinessException error = new BusinessException("文件存储服务暂不可用，请重试");
        error.initCause(cause);
        return error;
    }

    private boolean isMissing(Exception error, String code) {
        return error instanceof ErrorResponseException
                && code.equals(((ErrorResponseException) error).errorResponse().code());
    }

    private String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /** 单键 CAS 防止锁丢失后的旧持有者覆盖、续租或删除其他节点的新会话。 */
    private boolean compareChange(String key, String expected, String replacement, long ttl) {
        String script = "local v=redis.call('GET',KEYS[1]); "
                + "if (v or '')~=ARGV[1] then return 0 end; "
                + "if ARGV[3]=='0' then redis.call('DEL',KEYS[1]) "
                + "else redis.call('SET',KEYS[1],ARGV[2],'PX',ARGV[3]) end; return 1";
        return Long.valueOf(1).equals(RedisUtils.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(key), expected == null ? "" : expected, replacement, String.valueOf(ttl)));
    }

    /** 旧七天会话是视频，补齐新增类型字段但不重建上传。 */
    private void normalize(Upload upload) {
        if (upload.getBucket() == null || upload.getBucket().isBlank()) {
            upload.setBucket(minioBucketService.currentBucket());
        }
        if (upload.getTargetType() == null) upload.setTargetType(MessageTypeEnum.VIDEO);
        if (upload.getContentType() == null) upload.setContentType(
                VideoFormat.fromName(upload.getObjectKey().substring(
                        upload.getObjectKey().lastIndexOf('/') + 1)).getContentType());
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1
                || fileName.matches(".*[/\\\\\\p{Cntrl}?#].*")) {
            throw new BusinessException("文件名无效");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
