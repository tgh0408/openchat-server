package com.openchat.chat.service.abstracts;

import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.MessageUser;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.utils.AssertUtil;
import com.openchat.chat.domain.dto.SaveMsgArg;
import com.openchat.chat.service.MessageResourceService;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.transaction.service.MQProducer;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;

/**
 * Description: 消息处理器抽象类
 * Author:
 * Date: 2023-06-04
 */
public abstract class AbstractMsgHandler<T> {
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private MQProducer mqProducer;
    @Autowired
    private MessageResourceService resources;
    @Autowired
    private JsonMapper jsonMapper;

    private Class<T> bodyClass;

    @PostConstruct
    @SuppressWarnings("unchecked")
    private void init() {
        ParameterizedType genericSuperclass = (ParameterizedType) this.getClass().getGenericSuperclass();
        this.bodyClass = (Class<T>) genericSuperclass.getActualTypeArguments()[0];
        MsgHandlerFactory.register(getMsgTypeEnum(), this);
    }

    /**
     * 消息类型
     */
    abstract MessageTypeEnum getMsgTypeEnum();

    /**
     * 校验信息--保存前校验
     */
    public abstract void checkMsg(T body, Long roomId, Long uid, RoomTypeEnum roomType);

    /** 普通消息使用事务半消息；原位更新类型可重写此入口。 */
    public void sendMsg(ChatMessageReq request, RoomMessage roomMessage, Long uid) {
        validatedBody(request, roomMessage, uid);
        Long msgId = IdWorker.getId();
        mqProducer.sendTransactionMsg(MQConstant.SEND_MSG_TOPIC,
                new MsgSendMessageDTO(msgId, roomMessage.getRoomType()), msgId,
                new SaveMsgArg(request, uid, msgId));
    }

    public void checkAndSaveMsg(ChatMessageReq request, RoomMessage roomMessage, Long uid, Long msgId) {
        if (MessageResourceService.isAttachment(request.getMsgType())) resources.requireOwner(uid);
        // 事务落库时再次校验；文件助手仅免除好友、群成员校验，不免除内容校验。
        T body = validatedBody(request, roomMessage, uid);
        if (roomMessage.getRoomType().usesUserMessageTable()) {
            // 单聊和个人文件助手均只存一份，使用服务端解析后的真实房间号。
            MessageUser msg = new MessageUser();
            msg.setId(msgId);
            msg.setUid(uid);
            msg.setFromUid(uid);
            msg.setRoomId(roomMessage.getRoomId());
            msg.setType(request.getMsgType());
            msg.setStatus(YesOrNoEnum.NO);
            msg.setClientMsgId(request.getClientMsgId());
            fillMsgContent(msg, body, roomMessage.getRoomType());
            fillReply(msg, request, roomMessage.getRoomType());
            AssertUtil.isTrue(messageDao.save(roomMessage.getRoomType(), msg), "消息保存失败");
        } else {
            //统一保存
            Message insert = Message.builder()
                    .id(msgId)
                    .fromUid(uid)
                    .roomId(roomMessage.getRoomId())
                    .type(request.getMsgType())
                    .status(YesOrNoEnum.NO)
                    .clientMsgId(request.getClientMsgId())
                    .build();
            fillMsgContent(insert, body, RoomTypeEnum.GROUP);
            fillReply(insert, request, RoomTypeEnum.GROUP);
            AssertUtil.isTrue(messageDao.save(RoomTypeEnum.GROUP, insert), "消息保存失败");
        }
    }

    public T validatedBody(ChatMessageReq request, RoomMessage roomMessage, Long uid) {
        AssertUtil.isNotEmpty(request.getBody(), "消息内容不能为空");
        T body = this.toBean(request.getBody());
        AssertUtil.allCheckValidateThrow(body);
        checkReply(request, roomMessage);
        checkMsg(body, roomMessage.getRoomId(), uid, roomMessage.getRoomType());
        return body;
    }

    /** 引用目标不可访问时允许正文发送，但已知的非正常消息不能被引用。 */
    private void checkReply(ChatMessageReq request, RoomMessage room) {
        Long replyId = request.getReplyId();
        if (replyId == null) return;
        if (replyId <= 0) throw new BusinessException("引用消息 ID 必须为正数");
        if (request.getMsgType() == MessageTypeEnum.RECALL) {
            throw new BusinessException("撤回请求不能附带引用消息");
        }
        Message reply = messageDao.get(room.getRoomType(), room.getRoomId(), replyId);
        if (reply != null && !MessageTypeEnum.REPLAYABLE_TYPES.contains(reply.getType())) {
            throw new BusinessException("该消息类型不能被引用");
        }
    }

    /** 间隔为当前事务快照中的条数，不包含两端，不是雪花 ID 差值或精确跳转游标。 */
    private void fillReply(Message message, ChatMessageReq request, RoomTypeEnum roomType) {
        if (request.getReplyId() == null) return;
        if (request.getReplyId() >= message.getId()) {
            throw new BusinessException("引用消息必须早于当前消息，请重试");
        }
        message.setReplyMsgId(request.getReplyId());
        message.setGapCount(messageDao.countReplyGap(
                roomType, message.getRoomId(), request.getReplyId(), message.getId()));
    }

    //填充消息内容与拓展信息（落库前调用，一次 insert 直接带上 content/extra，避免先插空壳再 update）
    public abstract void fillMsgContent(Message message, T body, RoomTypeEnum roomTypeEnum);

    @SuppressWarnings("unchecked")
    private T toBean(Object body) {
        if (bodyClass.isAssignableFrom(body.getClass())) {
            return (T) body;
        }
        // 嵌套请求体与 HTTP 使用相同协议规则，避免数字枚举被当作下标。
        return jsonMapper.convertValue(body, bodyClass);
    }

    public abstract Object showMsg(Message message);

    public abstract Object showReplyMsg(Message replyMessage);

    /**
     * 会话列表——展示的消息
     */
    public abstract String showContactMsg(Message msg);

    /** 可按接收者生成提示；普通消息继续沿用原有摘要。 */
    public String showContactMsg(Message msg, Long receiveUid) {
        return showContactMsg(msg);
    }
}
