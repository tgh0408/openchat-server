package com.openchat.chat.service.abstracts;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.entity.msg.RtcCallMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.dto.SaveMsgArg;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.msg.RtcCallMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.AssertUtil;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;

/**
 * RTC 终态摘要：认领与消息落库同事务，可靠任务重试复用已提交的消息 ID。
 */
@Component
@RequiredArgsConstructor
public class RtcCallMsgHandler extends AbstractMsgHandler<RtcCallMsgReq> {
    private final RtcCallRepository calls;
    private final RoomAccessService access;
    private final MQProducer producer;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.RTC_CALL;
    }

    /** 先发半消息，认领及落库交给现有本地事务回调；重试沿用已绑定的消息 ID。 */
    @Override
    public void sendMsg(ChatMessageReq request, RoomMessage room, Long uid) {
        RtcCallRecord call = requireCall(request, room, uid);
        Long messageId = call.summaryMessageId() == null ? IdWorker.getId() : call.summaryMessageId();
        TransactionResolution resolution = producer.sendTransactionMsg(MQConstant.SEND_MSG_TOPIC,
                new MsgSendMessageDTO(messageId, room.getRoomType()), messageId,
                new SaveMsgArg(request, uid, messageId));
        AssertUtil.isTrue(resolution == TransactionResolution.COMMIT, "RTC 摘要事务未提交，请重试");
    }

    /** 本地事务入口同样必须认领；其他消息 ID 不得借已有摘要提交空的半消息。 */
    @Override
    public void checkAndSaveMsg(ChatMessageReq request, RoomMessage room, Long uid, Long msgId) {
        AssertUtil.isTrue(msgId != null && msgId > 0, "RTC 摘要消息 ID 无效");
        AssertUtil.isTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                "RTC 摘要必须在事务中保存");
        RtcCallRecord call = requireCall(request, room, uid);
        if (call.summaryMessageId() != null) {
            AssertUtil.isTrue(Objects.equals(msgId, call.summaryMessageId()), "RTC 摘要已绑定其他消息");
            return;
        }
        AssertUtil.isTrue(calls.claimSummaryMessage(call.callId(), msgId), "RTC 摘要认领冲突，请重试");
        super.checkAndSaveMsg(request, room, uid, msgId);
    }

    /** 发送前与本地事务中分别校验，不能用发送时的快照代替落库授权。 */
    private RtcCallRecord requireCall(ChatMessageReq request, RoomMessage room, Long uid) {
        RtcCallMsgReq body = validatedBody(request, room, uid);
        var actualRoom = access.requireCanSend(uid, room.getRoomId());
        AssertUtil.isTrue(actualRoom.getType() == room.getRoomType()
                && Objects.equals(request.getRoomId(), room.getRoomId()), "RTC 摘要房间不匹配");
        RtcCallRecord call = calls.findByCallId(body.getRtcCall().getCallId())
                .orElseThrow(() -> new BusinessException("RTC 通话不存在"));
        AssertUtil.isTrue(call.state().terminal(), "RTC 通话尚未结束");
        AssertUtil.isTrue(Objects.equals(call.roomId(), room.getRoomId())
                && Objects.equals(call.callerUid(), uid), "RTC 摘要不属于当前房间或发送者");
        AssertUtil.isTrue(room.getRoomType() == RoomTypeEnum.FRIEND
                || room.getRoomType() == RoomTypeEnum.GROUP, "当前房间不支持 RTC 摘要");
        return call;
    }

    @Override
    public void checkMsg(RtcCallMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
        AssertUtil.isNotEmpty(body, "RTC 摘要不能为空");
        AssertUtil.isNotEmpty(body.getContent(), "RTC 摘要内容不能为空");
        AssertUtil.isNotEmpty(body.getRtcCall(), "RTC 通话信息不能为空");
        AssertUtil.isNotEmpty(body.getRtcCall().getCallId(), "RTC callId 不能为空");
    }

    @Override
    public void fillMsgContent(Message message, RtcCallMsgReq body, RoomTypeEnum roomTypeEnum) {
        message.setContent(body.getContent());
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        extra.setRtcCall(body.getRtcCall());
        message.setExtra(extra);
    }

    @Override
    public Object showMsg(Message message) {
        TotalMsgResp response = new TotalMsgResp();
        response.setContent(message.getContent());
        response.setExtra(rtcCall(message));
        return response;
    }

    @Override
    public Object showReplyMsg(Message message) {
        return message.getContent();
    }

    @Override
    public String showContactMsg(Message message) {
        return message.getContent();
    }

    private RtcCallMsgDTO rtcCall(Message message) {
        MessageExtra extra = message.getExtra();
        return extra == null ? null : extra.getRtcCall();
    }
}
