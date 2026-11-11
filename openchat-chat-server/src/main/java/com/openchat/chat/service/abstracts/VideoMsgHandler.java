package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.entity.msg.VideoMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.msg.ImgMsgReq;
import com.openchat.chat.domain.vo.req.msg.VideoMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.domain.enums.VideoFormat;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import com.openchat.common.service.MinioBucketService;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class VideoMsgHandler extends AbstractMsgHandler<VideoMsgReq> {
    private final MinIOTemplate minIOTemplate;
    private final OssProperties ossProperties;
    private final ImgMsgHandler imgMsgHandler;
    private final MinioBucketService minioBucketService;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.VIDEO;
    }

    /** 视频和封面均由当前用户上传；只通过配置的 MinIO 客户端查询对象。 */
    @Override
    public void checkMsg(VideoMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
        if (uid == null || uid <= 0) throw new BusinessException("请先登录");
        VideoFormat format = VideoFormat.fromName(body.getFileName());
        String video = objectPath(body.getContent());
        String bucket = minioBucketService.currentBucket();
        String prefix = minioBucketService.prefix(bucket);
        if (!video.equals(objectPath(body.getUploadAddress()))
                || !video.matches(Pattern.quote(prefix) + "video/\\d{4}-\\d{2}/" + uid
                + "/[a-fA-F0-9-]{32,36}\\.(?i:mp4|mkv|avi|mov|wmv|flv|webm|mpg|mpeg)")
                || format != VideoFormat.fromName(video.substring(video.lastIndexOf('/') + 1))) {
            throw new BusinessException("只能发送本人上传的视频，且上传与下载地址必须一致");
        }
        String poster = objectPath(body.getThumbUrl());
        if (!poster.matches(Pattern.quote(prefix) + "img/\\d{4}-\\d{2}/" + uid
                + "/[a-fA-F0-9-]{32,36}\\.(?i:jpe?g)")) {
            throw new BusinessException("视频封面必须是本人上传的 JPEG 图片");
        }
        String objectName = video.substring(prefix.length());
        try {
            StatObjectResponse object = minIOTemplate.statObject(bucket, objectName);
            if (object.size() < 1 || object.size() > 2L * 1024 * 1024 * 1024
                    || !Long.valueOf(object.size()).equals(body.getFileSize())) {
                throw new BusinessException("视频大小不匹配或超过 2 GiB");
            }
            byte[] header = minIOTemplate.getObjectHeader(bucket, objectName, 32);
            if (!format.getContentType().equalsIgnoreCase(object.contentType()) || !format.matches(header)) {
                throw new BusinessException("视频格式、文件头与声明的容器不匹配");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("视频尚未上传或存储服务不可用，请重试");
        }
        // 复用图片对象与回复/@权限校验，封面不另建不受保护的上传通道。
        imgMsgHandler.checkMsg(ImgMsgReq.builder()
                .content(body.getThumbUrl()).uploadAddress(body.getThumbUrl())
                .fileName("poster.jpg").fileSize(body.getThumbSize())
                .replyMsgId(body.getReplyMsgId()).atUidList(body.getAtUidList()).build(),
                roomId, uid, roomType);
    }

    /** 拒绝外域、签名、编码路径和目录穿越；数据库只存对象路径。 */
    private String objectPath(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || uri.getRawPath() == null || uri.getRawPath().contains("%")
                    || uri.getRawPath().contains("..") || value.startsWith("//")) {
                throw new IllegalArgumentException();
            }
            String endpoint = ossProperties.getPublicEndpoint().replaceAll("/+$", "");
            String result;
            if (uri.isAbsolute()) {
                if (!value.startsWith(endpoint + "/")) throw new IllegalArgumentException();
                result = value.substring(endpoint.length());
            } else {
                result = value.startsWith("/") ? value : "/" + value;
            }
            OssUrlUtil.toPublicUrl(endpoint, result);
            return result;
        } catch (RuntimeException exception) {
            throw new BusinessException("视频或封面地址无效");
        }
    }

    @Override
    public void fillMsgContent(Message message, VideoMsgReq body, RoomTypeEnum roomType) {
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElseGet(MessageExtra::new);
        extra.setVideoMsgDTO(VideoMsgDTO.builder().size(body.getFileSize()).fileName(body.getFileName())
                .duration(body.getDuration()).thumbUrl(objectPath(body.getThumbUrl()))
                .thumbSize(body.getThumbSize()).thumbWidth(body.getThumbWidth())
                .thumbHeight(body.getThumbHeight()).build());
        extra.setAtUidList(body.getAtUidList());
        message.setExtra(extra);
        message.setContent(objectPath(body.getContent()));
    }

    /** SSE 与历史共用响应，转换时不修改缓存中的实体。 */
    @Override
    public Object showMsg(Message msg) {
        TotalMsgResp response = new TotalMsgResp();
        response.setContent(OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(), minioBucketService.currentBucket(), msg.getContent()));
        Optional.ofNullable(msg.getExtra()).map(MessageExtra::getVideoMsgDTO).ifPresent(video -> response.setExtra(VideoMsgDTO.builder().size(video.getSize()).fileName(video.getFileName())
                .duration(video.getDuration()).thumbWidth(video.getThumbWidth()).thumbHeight(video.getThumbHeight())
                .thumbSize(video.getThumbSize()).thumbUrl(video.getThumbUrl() == null ? null
                        : OssUrlUtil.toPublicUrl(
                        ossProperties.getPublicEndpoint(), minioBucketService.currentBucket(),
                        video.getThumbUrl())).build()));
        return response;
    }

    @Override
    public Object showReplyMsg(Message msg) {
        return "[视频]";
    }

    @Override
    public String showContactMsg(Message msg) {
        return "[视频]";
    }
}
