package com.openchat.chat.service;

import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.msg.MessageUpload;
import com.openchat.chat.domain.entity.msg.TemporaryMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.service.abstracts.TemporaryMsgHandler;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.openchat.chat.domain.enums.MessageTypeEnum.*;

/** 校验上传占位的归属和状态，更新由消息实体版本控制。 */
@Service
@RequiredArgsConstructor
public class MessageUploadService {
    private final MessageResourceService resources;
    private final RoomAccessService access;
    private final TemporaryMessageDao messages;

    public Message requireUploadMessage(Long uid, Long roomId, String clientMsgId) {
        if (roomId == null || roomId <= 0 || clientMsgId == null || clientMsgId.isBlank()
                || clientMsgId.length() > 64) throw new BusinessException("上传必须绑定有效占位消息");
        resources.requireOwner(uid);
        Room room = access.requireCurrentMember(uid, roomId);
        Message message = messages.find(room.getType(), roomId, uid, clientMsgId);
        if (message == null || !Objects.equals(message.getRoomId(), roomId)
                || !Objects.equals(message.getFromUid(), uid)
                || message.getStatus() != YesOrNoEnum.NO
                || !List.of(TEMP, VIDEO,FILE).contains(message.getType()))
            throw new BusinessException("上传消息已撤回或不存在");
        TemporaryMsgDTO metadata = TemporaryMsgHandler.metadata(message);
        if (metadata == null || metadata.isCancelled())
            throw new BusinessException("上传消息已取消");
        return message;
    }

    public void validateFile(Message message, MessageTypeEnum type, String name, Long size) {
        TemporaryMsgDTO metadata = TemporaryMsgHandler.metadata(message);
        MessageTypeEnum metadataType = metadata == null ? null : metadata.getTargetType();
        if (metadata == null || type == null || metadataType != type
                || !Objects.equals(metadata.getFileName(), name) || !Objects.equals(metadata.getSize(), size))
            throw new BusinessException("文件与上传占位不匹配");
    }

    public void bind(Message message, String sessionKey, String uploadId, String path) {
        resources.objectName(message.getFromUid(), path);
        MessageUpload upload = Optional.ofNullable(binding(message, sessionKey)).orElse(new MessageUpload());
        if (message.getType() != TEMP
                && (!Objects.equals(upload.getUploadId(), uploadId) || !Objects.equals(upload.getObjectPath(), path)))
            throw new BusinessException("已完成消息不能替换上传会话");
        upload.setSessionKey(sessionKey);
        upload.setUploadId(uploadId);
        upload.setObjectPath(path);
        message.getExtra().setUpload(upload);
        save(message);
    }

    /** 摘要首次绑定后不能更换；Redis 丢失时从数据库恢复原 uploadId，避免遗忘在途分片。 */
    public MessageUpload binding(Message message, String sessionKey) {
        MessageUpload upload = message.getExtra().getUpload();
        if (upload != null && upload.getSessionKey() != null
                && !Objects.equals(upload.getSessionKey(), sessionKey))
            throw new BusinessException("该占位已绑定其他文件摘要，请作为新消息发送");
        return upload;
    }

    public void requireBinding(Message message, String sessionKey, String uploadId, String path) {
        MessageUpload upload = message.getExtra().getUpload();
        if (upload == null || !Objects.equals(upload.getSessionKey(), sessionKey)
                || !Objects.equals(upload.getUploadId(), uploadId) || !Objects.equals(upload.getObjectPath(), path))
            throw new BusinessException("上传会话已变化，禁止旧请求继续操作");
    }

    /** 签名返回之前记录封面归属，覆盖封面已上传但尚未发起 Multipart 的撤回。 */
    public void bindPoster(Message message, String path) {
        resources.objectName(message.getFromUid(), path);
        TemporaryMsgDTO metadata = TemporaryMsgHandler.metadata(message);
        if (metadata == null || message.getType() != TEMP || metadata.getTargetType() != VIDEO)
            throw new BusinessException("只有未完成视频可以申请封面");
        MessageUpload upload = Optional.ofNullable(message.getExtra().getUpload()).orElse(new MessageUpload());
        if (upload.getPosterPaths().size() >= 32) throw new BusinessException("封面重试次数过多，请重新发送");
        upload.getPosterPaths().add(path);
        message.getExtra().setUpload(upload);
        save(message);
    }

    private void save(Message message) {
        Room room = access.requireCurrentMember(message.getFromUid(), message.getRoomId());
        if (!messages.update(room.getType(), message))
            throw new BusinessException("上传绑定保存失败");
    }
}
