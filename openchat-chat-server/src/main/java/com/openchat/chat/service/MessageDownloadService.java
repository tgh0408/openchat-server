package com.openchat.chat.service;

import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.domain.entity.Contact;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.msg.VideoMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.resp.MessageDownloadResp;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.common.service.MinioBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** 只为当前用户可见的已完成媒体消息签发短期下载凭证。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageDownloadService {
    private static final int EXPIRES_IN = 5 * 60;
    private final MessageDao messages;
    private final ContactDao contacts;
    private final RoomAccessService access;
    private final MinIOTemplate minio;
    private final OssProperties properties;
    private final MinioBucketService buckets;

    /** 同一数据库快照内判断权限和资源；已签发 URL 无法随随后发生的撤权即时失效。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MessageDownloadResp authorize(Long msgId, Long roomId, Long uid) {
        try {
            return authorizeInternal(msgId, roomId, uid);
        } catch (BusinessException exception) {
            log.warn("消息下载授权失败, msgId={}, roomId={}, uid={}, reason={}",
                    msgId, roomId, uid, exception.getMessage());
            throw exception;
        }
    }

    private MessageDownloadResp authorizeInternal(Long msgId, Long roomId, Long uid) {
        if (uid == null || uid <= 0) throw new BusinessException("请先登录");
        if (msgId == null || msgId <= 0) throw new BusinessException("无效的消息 ID");
        if (roomId == null || roomId <= 0) throw new BusinessException("无效的房间 ID");
        Room room = access.requireCanDownload(uid, roomId);
        RoomTypeEnum roomType = room.getType();
        if (roomType == null) {
            throw new BusinessException("消息不存在或不可下载");
        }
        Message message = messages.get(roomType, roomId, msgId);
        if (message == null || !Objects.equals(message.getRoomId(), roomId)
                || message.getStatus() != YesOrNoEnum.NO) {
            throw new BusinessException("消息不存在或不可下载");
        }
        if (roomType.usesUserMessageTable()) {
            Contact contact = contacts.getContactStatus(room.getId(), uid);
            if (contact != null && contact.getLastDeleteId() != null && msgId <= contact.getLastDeleteId()) {
                throw new BusinessException("消息已超出可见历史范围");
            }
        }
        MessageTypeEnum type = message.getType();
        if (type == null) throw new BusinessException("该消息不支持附件下载");
        String directory = type.name().toLowerCase(Locale.ROOT);
        String bucket = buckets.currentBucket();
        String objectKey = objectKey(
                message.getContent(), bucket, directory, message.getFromUid(), "content");
        String thumbnailKey;
        if (type == MessageTypeEnum.VIDEO) {
            VideoMsgDTO video = message.getExtra() == null ? null : message.getExtra().getVideoMsgDTO();
            if (video != null && video.getThumbUrl() != null) {
                thumbnailKey = objectKey(
                        video.getThumbUrl(), bucket, "img", message.getFromUid(), "thumbnail");
            }else {
                thumbnailKey = null;
            }
        }else {
            thumbnailKey = null;
        }
        try {
            String downloadUrl = minio.getPresignedDownloadUrl(bucket, objectKey, EXPIRES_IN);
            String thumbUrl = thumbnailKey == null ? null
                    : minio.getPresignedDownloadUrl(bucket, thumbnailKey, EXPIRES_IN);
            return MessageDownloadResp.builder()
                    .msgId(msgId)
                    .downloadUrl(downloadUrl)
                    .thumbDownloadUrl(thumbUrl)
                    .expiresIn(EXPIRES_IN)
                    .build();
        } catch (Exception exception) {
            // SDK 异常可能含存储地址或签名参数，不将其直接暴露给调用者。
            log.error("消息附件签名失败, msgId={}, uid={}, roomId={}, type={}, bucket={}, objectKey={}, thumbnailKey={}",
                    msgId, uid, message.getRoomId(), type, bucket, objectKey, thumbnailKey, exception);
            throw new BusinessException("附件下载授权失败，请稍后重试");
        }
    }

    /** 只解析本站已落库对象，按消息发送者限制目录，禁止签发任意桶或外域资源。 */
    private String objectKey(String value, String bucket, String directory, Long senderUid, String resourceName) {
        String path = null;
        try {
            if (value == null || senderUid == null || senderUid <= 0) {
                throw new IllegalArgumentException("资源值或发送者 UID 为空");
            }
            URI uri = URI.create(value);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || uri.getRawPath() == null) {
                throw new IllegalArgumentException("资源包含 query、fragment、用户信息或空路径");
            }
            if (value.contains("%") || value.contains("..")) {
                throw new IllegalArgumentException("资源包含编码字符或路径穿越片段");
            }
            if (value.startsWith("//")) {
                throw new IllegalArgumentException("资源使用了不允许的网络路径格式");
            }
            if (uri.isAbsolute()) {
                String publicEndpoint = properties.getPublicEndpoint();
                if (publicEndpoint == null || publicEndpoint.isBlank()) {
                    throw new IllegalArgumentException("OSS public-endpoint 未配置");
                }
                URI endpoint = URI.create(publicEndpoint);
                if (!Objects.equals(uri.getScheme(), endpoint.getScheme())
                        || !Objects.equals(uri.getHost(), endpoint.getHost())
                        || uri.getPort() != endpoint.getPort()
                        || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                        || endpoint.getUserInfo() != null) {
                    throw new IllegalArgumentException("资源地址与 OSS public-endpoint 不匹配");
                }
                String endpointPath = endpoint.getRawPath() == null ? "" : endpoint.getRawPath();
                endpointPath = endpointPath.replaceAll("/+$", "");
                String resourcePath = uri.getRawPath();
                if (!resourcePath.startsWith(endpointPath + "/")) {
                    throw new IllegalArgumentException("资源不在 OSS public-endpoint 路径下");
                }
                path = resourcePath.substring(endpointPath.length());
            } else {
                path = uri.getRawPath();
            }
            if (path.startsWith("/")) path = path.substring(1);
            if (path.startsWith(bucket + "/")) path = path.substring(bucket.length() + 1);
            String objectPattern = directory + "/\\d{4}-\\d{2}/" + senderUid
                    + "/[a-fA-F0-9-]{32,36}\\.[a-zA-Z0-9]+";
            if (!path.matches(objectPattern)) {
                throw new IllegalArgumentException("对象路径不匹配期望格式");
            }
            return path;
        } catch (IllegalArgumentException exception) {
            log.warn("消息附件路径解析失败, resource={}, bucket={}, directory={}, senderUid={}, path={}, value={}, reason={}",
                    resourceName, bucket, directory, senderUid, path, safeResourcePath(value), exception.getMessage());
            throw new BusinessException("消息附件路径无效");
        }
    }

    private String safeResourcePath(String value) {
        if (value == null) return "<null>";
        try {
            URI uri = URI.create(value);
            String path = uri.getRawPath();
            if (path != null) return truncate(path);
        } catch (IllegalArgumentException ignored) {
            // 仅用于日志脱敏，原始解析异常仍由调用方统一处理。
        }
        return "<unparseable,length=" + value.length() + ">";
    }

    private String truncate(String value) {
        return value.length() <= 256 ? value : value.substring(0, 256) + "...";
    }

}
