package com.openchat.chat.service.abstracts;


import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.entity.msg.MsgRecall;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.msg.RecallMsgReq;
import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.chat.service.MessageResourceService;
import com.openchat.chat.service.DisplayNameService;
import com.openchat.chat.service.RecallResourceCleanup;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.chat.service.TemporaryMessagePublisher;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.exception.BusinessException;
import com.openchat.chat.service.MessageRecallPermission;
import com.openchat.user.dao.UserDao;
import com.openchat.chat.domain.vo.resp.RecallMsgResp;
import com.openchat.user.domain.enums.IdTypeEnum;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;

/**
 * Description: 经统一发送入口原位撤回消息，并可靠清理附件。
 * Author:
 * Date: 2023-06-04
 */
@Component
@RequiredArgsConstructor
public class RecallMsgHandler extends AbstractMsgHandler<RecallMsgReq> {
    private final TemporaryMessageDao messages;
    private final RoomAccessService access;
    private final MessageRecallPermission permissions;
    private final MessageResourceService resources;
    private final RecallResourceCleanup cleanup;
    private final TemporaryMessagePublisher publisher;
    private final PlatformTransactionManager transactionManager;
    private final UserDao users;
    private final DisplayNameService displayNames;

    private static final List<MessageTypeEnum> allowRecallList = List.of(MessageTypeEnum.TEXT,MessageTypeEnum.IMG,MessageTypeEnum.VIDEO,MessageTypeEnum.FILE,MessageTypeEnum.EMOJI,MessageTypeEnum.SOUND);

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.RECALL;
    }

    @Override
    public void checkMsg(RecallMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
        access.requireCurrentMember(uid, roomId);
        Message message = messages.list(roomType, List.of(body.getMsgId())).stream().findFirst().orElse(null);
        checkTarget(message, roomId, uid, roomType);
    }

    @Override
    public void fillMsgContent(Message msg, RecallMsgReq body, RoomTypeEnum roomTypeEnum) {
        throw new UnsupportedOperationException("撤回只能原位更新消息");
    }

    @Override
    public void checkAndSaveMsg(ChatMessageReq request, RoomMessage room, Long uid, Long ignored) {
        RecallMsgReq body = validatedBody(request, room, uid);
        Message message = messages.getById(room.getRoomType(), body.getMsgId());
        if (message == null) throw new BusinessException("消息不存在");
        resources.requireOwner(message.getFromUid());
        access.requireCurrentMember(uid, room.getRoomId());
        checkTarget(message, room.getRoomId(), uid, room.getRoomType());
        if (message.getType() != MessageTypeEnum.RECALL) {
            List<String> paths = resources.paths(message);
            var upload = message.getExtra() == null ? null : message.getExtra().getUpload();
            message.setExtra(MessageExtra.builder().recall(MsgRecall.builder()
                    .recallUid(uid).recallTime(new Date()).objectPaths(paths).upload(upload).build()).build());
            message.setContent("");
            message.setType(MessageTypeEnum.RECALL);
            message.setUpdateTime(new Date());
            if (!messages.update(room.getRoomType(), message)) throw new BusinessException("撤回失败，请重试");
            if (upload != null && upload.getUploadId() != null)
                cleanup.deleteUpload(message.getFromUid(), upload, paths);
            else if (!paths.isEmpty()) cleanup.deleteObjects(message.getFromUid(), paths);
        }
        // 重复撤回重推终态，不推进房间活跃位置或已读游标。
        publisher.publish(new MsgSendMessageDTO(message.getId(), room.getRoomType(), true));
        messages.invalidateAfterCommit(room.getRoomType(), message.getRoomId(), message.getId());
    }

    /** 旧消息存在不能证明撤回事务提交；更新与可靠调用记录使用同一事务。 */
    @Override
    public void sendMsg(ChatMessageReq request, RoomMessage room, Long uid) {
        // 本类无 AOP 代理：工厂在 @PostConstruct 注册的是原始实例，方法上的 @Transactional 不生效。
        // 事务覆盖消息更新、资源引用和可靠调用记录。
        new TransactionTemplate(transactionManager).executeWithoutResult(
                _ -> checkAndSaveMsg(request, room, uid, null));
    }

    private void checkTarget(Message message, Long roomId, Long uid, RoomTypeEnum type) {
        if (message == null || !Objects.equals(message.getRoomId(), roomId)
                || message.getStatus() != com.openchat.common.domain.enums.YesOrNoEnum.NO) throw new BusinessException("消息不存在");
        boolean manager = permissions.canManage(uid, roomId, type);
        if (!Objects.equals(message.getFromUid(), uid) && !manager)
            throw new BusinessException("不可以撤回别人的消息");
        if (message.getType() == MessageTypeEnum.RECALL) return;
        if (message.getType() == MessageTypeEnum.TEMP && manager) return;
        if (!allowRecallList.contains(message.getType()))
            throw new BusinessException("该消息不能撤回，上传中的消息请取消发送");
        if (!manager && (message.getCreateTime() == null
                || System.currentTimeMillis() - message.getCreateTime().getTime() > 120_000L))
            throw new BusinessException("超过2分钟的消息不能撤回");
    }

    @Override
    public RecallMsgResp showMsg(Message msg) {
        MsgRecall recall = msg.getExtra() == null ? null : msg.getExtra().getRecall();
        Long uid = recall == null ? null : recall.getRecallUid();
        if (uid == null || uid <= 0) return new RecallMsgResp(null, null, null);
        var user = users.get(IdTypeEnum.ID, uid);
        String name = user == null ? null : user.getName();
        return new RecallMsgResp(uid, name, displayNames.groupNickname(msg.getRoomId(), uid));
    }

    @Override
    public Object showReplyMsg(Message msg) {
        return "原消息已被撤回";
    }


    @Override
    public String showContactMsg(Message msg) {
        return showContactMsg(msg, null);
    }

    @Override
    public String showContactMsg(Message msg, Long receiveUid) {
        RecallMsgResp body = showMsg(msg);
        if (body.getRecallUid() == null) return "消息已撤回";
        if (Objects.equals(receiveUid, body.getRecallUid())) return "你撤回了一条消息";
        return displayNames.resolve(receiveUid, msg.getRoomId(), body.getRecallUid()) + "撤回了一条消息";
    }
}
