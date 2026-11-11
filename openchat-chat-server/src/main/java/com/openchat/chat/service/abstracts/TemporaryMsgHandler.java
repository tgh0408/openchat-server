package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.*;
import com.openchat.chat.domain.enums.*;
import com.openchat.chat.domain.vo.req.msg.TemporaryMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.common.domain.enums.VideoFormat;
import com.openchat.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/** 视频和普通大文件共用占位；占位不包含可播放地址，不接收客户端声明的终态。 */
@Component
public class TemporaryMsgHandler extends AbstractMsgHandler<TemporaryMsgReq> {
    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.TEMP;
    }

    @Override
    public void checkMsg(TemporaryMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
        if (uid == null || uid <= 0) throw new BusinessException("请先登录");
        MessageTypeEnum targetType = body.getTargetType();
        if (targetType != MessageTypeEnum.VIDEO && targetType != MessageTypeEnum.FILE)
            throw new BusinessException("占位只支持视频或文件");
        if (body.getFileSize() == null || body.getFileSize() < 1
                || body.getFileSize() > (targetType == MessageTypeEnum.VIDEO
                ? 2L * 1024 * 1024 * 1024 : 6L * 1024 * 1024 * 1024)) {
            throw new BusinessException(targetType == MessageTypeEnum.VIDEO
                    ? "视频不能超过 2 GiB" : "文件不能超过 6 GiB");
        }
        if (body.getFileName().contains("/") || body.getFileName().contains("\\")
                || body.getFileName().chars().anyMatch(Character::isISOControl))
            throw new BusinessException("文件名无效");
        if (targetType == MessageTypeEnum.VIDEO) {
            VideoFormat.fromName(body.getFileName());
            if (body.getDuration() == null || body.getThumbWidth() == null || body.getThumbHeight() == null)
                throw new BusinessException("视频占位缺少时长或封面尺寸");
        } else if (body.getDuration() != null || body.getThumbWidth() != null || body.getThumbHeight() != null) {
            throw new BusinessException("普通文件占位不接受视频字段");
        }
    }

    @Override
    public void fillMsgContent(Message message, TemporaryMsgReq body, RoomTypeEnum type) {
        MessageExtra extra = new MessageExtra();
        extra.setTemporary(TemporaryMsgDTO.builder()
                        .targetType(body.getTargetType())
                .fileName(body.getFileName())
                .size(body.getFileSize())
                .duration(body.getDuration())
                .thumbWidth(body.getThumbWidth())
                .thumbHeight(body.getThumbHeight())
                .cancelled(false)
                .build());
        message.setExtra(extra);
        message.setContent(summary(extra.getTemporary()));
    }

    /** 兼容此前已落库的视频占位，不迁移或清除历史记录。 */
    public static TemporaryMsgDTO metadata(Message message) {
        MessageExtra extra = message.getExtra();
        if (extra == null) return null;
        if (extra.getTemporary() != null) return extra.getTemporary();
        VideoMsgDTO video = extra.getVideoMsgDTO();
        if (video == null) return null;
        return TemporaryMsgDTO.builder()
                .targetType(MessageTypeEnum.VIDEO)
                .fileName(video.getFileName())
                .size(video.getSize())
                .duration(video.getDuration())
                .thumbWidth(video.getThumbWidth())
                .thumbHeight(video.getThumbHeight())
                .cancelled(false)
                .build();
    }

    private static String summary(TemporaryMsgDTO data) {
        if (data == null) return "[文件上传中]";
        if (data.isCancelled()) return "[已取消发送]";
        return data.getTargetType() == MessageTypeEnum.VIDEO
                ? "[视频上传中]" : "[文件上传中]";
    }

    @Override
    public Object showMsg(Message message) {
        TotalMsgResp response = new TotalMsgResp();
        response.setContent(summary(metadata(message)));
        response.setExtra(metadata(message));
        return response;
    }

    @Override
    public Object showReplyMsg(Message message) {
        return summary(metadata(message));
    }

    @Override
    public String showContactMsg(Message message) {
        return summary(metadata(message));
    }
}
