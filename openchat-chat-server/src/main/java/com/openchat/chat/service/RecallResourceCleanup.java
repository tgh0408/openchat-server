package com.openchat.chat.service;

import com.openchat.chat.domain.entity.msg.MessageUpload;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.MultipartClient;
import com.openchat.oss.OssProperties;
import com.openchat.transaction.annotation.SecureInvoke;
import com.openchat.common.service.MinioBucketService;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 清理记录与撤回同事务保存，提交后删除；失败保留可靠调用记录，不回滚已确认撤回。 */
@Service
@RequiredArgsConstructor
public class RecallResourceCleanup {
    private final MessageResourceService resources;
    private final MinIOTemplate minio;
    private final MultipartClient multipart;
    private final OssProperties properties;
    private final MinioBucketService minioBucketService;

    /** 不依赖 Redis 七天记录，撤回事务保存的 uploadId 足以在重启后继续终止。 */
    @SecureInvoke(maxRetryTimes = 10)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteUpload(Long uid, MessageUpload upload, List<String> paths) {
        resources.requireOwner(uid);
        String object = resources.objectName(uid, upload.getObjectPath());
        String bucket = minioBucketService.currentBucket();
        try {
            multipart.abort(bucket, properties.getRegion(), object, upload.getUploadId());
        } catch (Exception error) {
            if (!missingUpload(error)) throw error;
        }
        // Abort 后核对分片；存储仍有在途分片时让可靠调用重试，不提前确认清理完成。
        try {
            if (!multipart.parts(bucket, properties.getRegion(), object,
                    upload.getUploadId()).isEmpty()) throw new IllegalStateException("上传分片尚未清理完成");
        } catch (Exception error) {
            if (!missingUpload(error)) throw error;
        }
        removeObjects(uid, paths);
    }

    private boolean missingUpload(Exception error) {
        return error instanceof ErrorResponseException
                && "NoSuchUpload".equals(((ErrorResponseException) error).errorResponse().code());
    }

    @SecureInvoke(maxRetryTimes = 10)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteObjects(Long uid, List<String> paths) {
        resources.requireOwner(uid);
        removeObjects(uid, paths);
    }

    private void removeObjects(Long uid, List<String> paths) {
        String bucket = minioBucketService.currentBucket();
        for (String path : paths) {
            String object = resources.objectName(uid, path);
            minio.removeObject(bucket, object);
        }
    }
}
