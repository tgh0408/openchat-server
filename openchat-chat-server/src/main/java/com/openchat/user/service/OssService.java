package com.openchat.user.service;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.service.MessageUploadService;
import com.openchat.common.domain.enums.OssSceneEnum;
import com.openchat.common.domain.enums.VideoFormat;
import com.openchat.common.exception.BusinessException;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import com.openchat.oss.domain.OssReq;
import com.openchat.oss.domain.OssResp;
import com.openchat.user.domain.vo.oss.UploadFileReq;
import com.openchat.user.domain.vo.oss.UploadUrlReq;
import com.openchat.common.service.MinioBucketService;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OssService {
    private final MinIOTemplate minIOTemplate;
    private final OssProperties ossProperties;
    private final MessageUploadService messageUploads;
    private final MinioBucketService minioBucketService;

    /** 获取临时上传链接，沿用场景与消息占位校验。 */
    @Transactional(rollbackFor = Exception.class)
    public OssResp getUploadUrl(Long uid, UploadUrlReq req) {
        OssSceneEnum sceneEnum = req.getScene();
        if (Objects.isNull(sceneEnum)) {
            throw new BusinessException("Invalid OSS scene");
        }
        if (sceneEnum == OssSceneEnum.IMG && (req.getFileName().length() > 255 || !req.getFileName().matches("(?i)[^/\\\\\\p{Cntrl}]+\\.(png|jpe?g|gif|bmp)"))) {
            throw new BusinessException("Unsupported image file");
        }
        if (sceneEnum == OssSceneEnum.VIDEO) {
            VideoFormat.fromName(req.getFileName());
        }
        Message placeholder = null;
        if (sceneEnum == OssSceneEnum.SOUND &&  !req.getFileName().matches("[^/\\\\\\p{Cntrl}]{1,251}\\.wav")) {
            throw new BusinessException("Only WAV audio is supported");
        }
        if (req.getRoomId() != null || req.getClientMsgId() != null) {
            if (sceneEnum != OssSceneEnum.IMG || !"poster.jpg".equals(req.getFileName())) {
                throw new BusinessException("Only poster.jpg supports message placeholders");
            }
            placeholder = messageUploads.requireUploadMessage(uid, req.getRoomId(), req.getClientMsgId());
        }
        OssReq ossReq = OssReq.builder()
                .fileName(req.getFileName())
                .filePath(sceneEnum.getPath())
                .uid(uid)
                .build();
        String bucket = minioBucketService.currentBucket();
        OssResp response = minIOTemplate.getPreSignedObjectUrl(ossReq, bucket);
        if (placeholder != null) {
            messageUploads.bindPoster(placeholder, response.getDownloadUrl());
        }
        if (sceneEnum == OssSceneEnum.IMG || sceneEnum == OssSceneEnum.VIDEO
                || sceneEnum == OssSceneEnum.SOUND) {
            response.setDownloadUrl(OssUrlUtil.toPublicUrl(
                    ossProperties.getPublicEndpoint(), response.getDownloadUrl()));
        }
        return response;
    }

    public String uploadFile(UploadFileReq req) throws
            IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (req != null && (req.getScene() == null || req.getScene() != OssSceneEnum.EMOJI)) {
            throw new BusinessException("Only emoji files can be uploaded through this endpoint");
        }
        try {
            return minIOTemplate.upLoadFile(minioBucketService.currentBucket(),
                    req.getFilePath(), req.getStream());
        } catch (MinioException error) {
            throw new BusinessException("Emoji upload failed");
        }
    }

    public byte[] getObject(String path) {
        String bucket = minioBucketService.currentBucket();
        return minIOTemplate.getObject(bucket, minioBucketService.objectKey(path));
    }
}
