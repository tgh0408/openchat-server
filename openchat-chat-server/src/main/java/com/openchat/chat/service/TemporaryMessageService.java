package com.openchat.chat.service;

import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.entity.*;
import com.openchat.chat.domain.entity.msg.VideoMsgDTO;
import com.openchat.chat.domain.entity.msg.TemporaryMsgDTO;
import com.openchat.chat.domain.enums.*;
import com.openchat.chat.domain.vo.req.*;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.service.ChatService;
import com.openchat.chat.service.abstracts.TemporaryMsgHandler;
import com.openchat.chat.service.abstracts.VideoMsgHandler;
import com.openchat.chat.service.abstracts.FileMsgHandler;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.openchat.chat.domain.vo.req.msg.TemporaryMsgReq;

/** 文件和视频先发布共用占位，再经对象校验完成或取消。 */
@Service
@RequiredArgsConstructor
public class TemporaryMessageService {
    private final RoomAccessService access;
    private final TemporaryMessageDao dao;
    private final TemporaryMsgHandler temporary;
    private final VideoMsgHandler videos;
    private final FileMsgHandler files;
    private final ChatService chat;
    private final ContactDao contacts;
    private final TemporaryMessagePublisher publisher;
    private final MessageResourceService resources;

    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResp create(ChatMessageReq request, Long uid) {
        Room room = access.requireCanSend(uid, request.getRoomId());
        RoomTypeEnum type = room.getType();
        if (request.getMsgType() != MessageTypeEnum.TEMP
                || request.getClientMsgId() == null || request.getClientMsgId().length() > 64
                || request.getClientMsgId().isBlank()) {
            throw new BusinessException("无效的文件占位请求");
        }
        RoomMessage context = RoomMessage.builder().roomId(room.getId()).roomType(type).userId(uid).build();
        TemporaryMsgReq expected = temporary.validatedBody(request, context, uid);
        Message message = dao.find(type, room.getId(), uid, request.getClientMsgId());
        if (message == null) {
            Long id = IdWorker.getId();
            temporary.checkAndSaveMsg(request, context, uid, id);
            message = dao.getById(type, id);
            if (type == RoomTypeEnum.FILE_ASSISTANT) {
                contacts.createMissingContacts(room.getId(), List.of(uid));
                contacts.changeContactStatus(room.getId(), uid);
            }
            publisher.publish(new MsgSendMessageDTO(id, type));
        } else if (message.getStatus() != com.openchat.common.domain.enums.YesOrNoEnum.NO
                || (message.getType() != MessageTypeEnum.TEMP
                && message.getType() != MessageTypeEnum.VIDEO
                && message.getType() != MessageTypeEnum.FILE)) {
            throw new BusinessException("客户端消息标识已被使用");
        }
        TemporaryMsgDTO stored = TemporaryMsgHandler.metadata(message);
        if (stored == null || !Objects.equals(stored.getFileName(), expected.getFileName())
                || !Objects.equals(message.getReplyMsgId(), request.getReplyId())
                || stored.getTargetType() == null
                || stored.getTargetType() != expected.getTargetType()
                || !Objects.equals(stored.getSize(), expected.getFileSize())
                || !Objects.equals(stored.getDuration(), expected.getDuration())
                || !Objects.equals(stored.getThumbWidth(), expected.getThumbWidth())
                || !Objects.equals(stored.getThumbHeight(), expected.getThumbHeight())) {
            throw new BusinessException("客户端消息标识对应其他文件");
        }
        return chat.getMsgResp(message, uid);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(TemporaryCompleteReq request, Long uid) {
        Room room = access.requireCanSend(uid, request.getRoomId());
        RoomTypeEnum type = room.getType();
        resources.requireOwner(uid);
        Message message = dao.getById(type, request.getMsgId());
        if (message == null || !Objects.equals(message.getRoomId(), room.getId())
                || !Objects.equals(message.getFromUid(), uid)
                || !Objects.equals(message.getClientMsgId(), request.getClientMsgId())
                || message.getStatus() != YesOrNoEnum.NO
                || !MessageTypeEnum.equalsIn(message.getType(), MessageTypeEnum.TEMP,MessageTypeEnum.VIDEO,MessageTypeEnum.FILE)) {
            throw new BusinessException("无权替换该上传占位");
        }
        TemporaryMsgDTO placeholder = TemporaryMsgHandler.metadata(message);
        if (placeholder == null || placeholder.isCancelled() || !MessageTypeEnum.equalsIn(placeholder.getTargetType(),MessageTypeEnum.VIDEO,MessageTypeEnum.FILE))
            throw new BusinessException("占位已取消或类型无效");
        MessageTypeEnum targetType = placeholder.getTargetType();
        boolean video = targetType == MessageTypeEnum.VIDEO;
        if (video ? request.getBody() == null || request.getFileBody() != null
                : request.getFileBody() == null || request.getBody() != null) {
            throw new BusinessException("完成请求与占位类型不匹配");
        }
        MessageTypeEnum finalType = targetType == MessageTypeEnum.VIDEO
                ? MessageTypeEnum.VIDEO : MessageTypeEnum.FILE;
        ChatMessageReq completedRequest = ChatMessageReq.builder()
                .roomId(room.getId())
                .msgType(finalType)
                .clientMsgId(request.getClientMsgId())
                .body(video ? request.getBody() : request.getFileBody())
                .build();
        RoomMessage context = RoomMessage.builder().roomId(room.getId()).roomType(type).userId(uid).build();
        if (targetType == MessageTypeEnum.VIDEO) {
            videos.validatedBody(completedRequest, context, uid);
        } else {
            files.validatedBody(completedRequest, context, uid);
        }
        String fileName = video ? request.getBody().getFileName() : request.getFileBody().getFileName();
        Long fileSize = video ? request.getBody().getFileSize() : request.getFileBody().getFileSize();
        if (!Objects.equals(placeholder.getFileName(), fileName)
                || !Objects.equals(placeholder.getSize(), fileSize)
                || (video && (!Objects.equals(placeholder.getDuration(), request.getBody().getDuration())
                || !Objects.equals(placeholder.getThumbWidth(), request.getBody().getThumbWidth())
                || !Objects.equals(placeholder.getThumbHeight(), request.getBody().getThumbHeight())))) {
            throw new BusinessException("文件与占位元数据不一致");
        }
        Message completed = new Message();
        completed.setFromUid(uid);
        completed.setType(finalType);
        if (targetType == MessageTypeEnum.VIDEO) {
            videos.fillMsgContent(completed, request.getBody(), type);
        } else {
            files.fillMsgContent(completed, request.getFileBody(), type);
        }
        if (message.getType() == finalType) {
            if (!Objects.equals(message.getContent(), completed.getContent())
                    || (video
                    ? !Objects.equals(message.getExtra().getVideoMsgDTO(), completed.getExtra().getVideoMsgDTO())
                    : !Objects.equals(message.getExtra().getFileMsg(), completed.getExtra().getFileMsg()))) {
                throw new BusinessException("已完成的消息不能替换成其他文件");
            }
        } else {
            var upload = message.getExtra().getUpload();
            if (upload != null && (!Objects.equals(upload.getObjectPath(), completed.getContent())
                    || (video && !upload.getPosterPaths().contains(
                    completed.getExtra().getVideoMsgDTO().getThumbUrl()))))
                throw new BusinessException("完成对象与占位绑定的上传不匹配");
            access.requireCurrentMember(uid, room.getId());
            message.setContent(completed.getContent());
            message.setExtra(completed.getExtra());
            message.getExtra().setTemporary(placeholder);
            message.getExtra().setUpload(upload);
            message.setType(finalType);
            message.setUpdateTime(new Date());
            if (!dao.update(type, message)) throw new BusinessException("上传占位替换失败");
        }
        // 重试只重推同一条最终消息，不新增消息，不推进已读或活跃时间。
        publisher.publish(new MsgSendMessageDTO(message.getId(), type, true));
        dao.invalidateAfterCommit(type, message.getRoomId(), message.getId());
    }

    /** 只取消本人的未完成占位，重复取消幂等；状态更新由消息版本控制。 */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResp cancel(TemporaryCancelReq request, Long uid) {
        Room room = access.requireCurrentMember(uid, request.getRoomId());
        RoomTypeEnum type = room.getType();
        Message message = dao.find(type, room.getId(), uid, request.getClientMsgId());
        if (message == null || !Objects.equals(message.getFromUid(), uid)
                || !Objects.equals(message.getRoomId(), room.getId())
                || message.getStatus() != YesOrNoEnum.NO
                || message.getType() != MessageTypeEnum.TEMP)
            throw new BusinessException("消息已完成或不存在，无法取消上传");
        TemporaryMsgDTO data = TemporaryMsgHandler.metadata(message);
        if (data == null) throw new BusinessException("占位元数据无效");
        access.requireCurrentMember(uid, room.getId());
        if (!data.isCancelled()) {
            data.setCancelled(true);
            message.getExtra().setTemporary(data);
            message.setContent("[已取消发送]");
            message.setUpdateTime(new Date());
            if (!dao.update(type, message)) throw new BusinessException("取消发送失败");
        }
        publisher.publish(new MsgSendMessageDTO(message.getId(), type, true));
        dao.invalidateAfterCommit(type, message.getRoomId(), message.getId());
        return chat.getMsgResp(message, uid);
    }

    public List<ChatMessageResp> state(TemporaryStateReq request, Long uid) {
        return chat.getTemporaryMsgState(request, uid);
    }
}
