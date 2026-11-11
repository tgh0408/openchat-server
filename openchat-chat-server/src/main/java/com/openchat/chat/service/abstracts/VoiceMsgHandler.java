package com.openchat.chat.service.abstracts;

import cn.hutool.core.net.url.UrlBuilder;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.FileMsgDTO;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.msg.VoiceMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.dao.UserDao;
import com.openchat.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VoiceMsgHandler extends AbstractMsgHandler<VoiceMsgReq> {
    final RoomFriendDao roomFriendDao;
    final UserService userService;
    final UserDao userDao;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.VOICE;
    }

    @Override
    public void checkMsg(VoiceMsgReq body, Long roomId, Long uid,RoomTypeEnum roomType) {
        //校验回复消息是否存在和是否在相同的聊天环境下
//        if (Objects.nonNull(body.getReplyMsgId())) {
//            Message replyMsg = messageDao.getById(body.getReplyMsgId());
//            if (Objects.isNull(replyMsg)) {
//                throw new BusinessException("回复消息不存在");
//            }
//            if (!StringUtils.equals(replyMsg.getRoomId().toString(), roomId.toString())) {
//                throw new BusinessException("只能回复相同会话内的消息");
//            }
//        }
//        if (CollectionUtil.isNotEmpty(body.getAtUidList())) {
//            //前端传入的@用户列表可能会重复，需要去重
//            List<Long> atUidList = body.getAtUidList().stream().distinct().collect(Collectors.toList());
//            Map<Long, User> batch = userService.getBatch(atUidList);
//            //如果@用户不存在，userDao 返回的map中依然存在该key，但是value为null，需要过滤掉再校验
//            long batchCount = batch.values().stream().filter(Objects::nonNull).count();
//            if (batchCount != atUidList.size()) {
//                throw new BusinessException("@用户不存在");
//            }
//        }

    }

    @Override
    public void fillMsgContent(Message message, VoiceMsgReq body, RoomTypeEnum roomTypeEnum) {
        // 文件上传时先请求OssController接口上传文件后将路径传回来
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        FileMsgDTO fileMsgDTO = new FileMsgDTO();
        fileMsgDTO.setFileName(body.getFileName());
        fileMsgDTO.setUpLoadPath(UrlBuilder.ofHttp(body.getUploadAddress()).getPath().toString());
        fileMsgDTO.setSize(body.getFileSize());
        extra.setFileMsg(fileMsgDTO);
        message.setExtra(extra);
        message.setContent(UrlBuilder.ofHttp(body.getContent()).getPath().toString());
    }

    @Override
    public Object showMsg(Message msg) {
        TotalMsgResp resp = new TotalMsgResp();
        resp.setContent(msg.getContent());
        resp.setUrlContentMap(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getUrlContentMap).orElse(null));
        resp.setAtUidList(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getAtUidList).orElse(null));
        //消息拓展 文件名
        FileMsgDTO fileMsg = Optional.ofNullable(msg.getExtra())
                .orElse(new MessageExtra())
                .getFileMsg();
        fileMsg.setUpLoadPath(null);
        resp.setExtra(fileMsg);
        //回复消息
//        Optional<Message> reply = Optional.ofNullable(msg.getReplyMsgId())
//                .map(messageDao::getById)
//                .filter(a -> Objects.equals(a.getStatus(), YesOrNoEnum.NO.getStatus()));
//        if (reply.isPresent()) {
//            Message replyMessage = reply.get();
//            TotalMsgResp.ReplyMsg replyMsgVO = new TotalMsgResp.ReplyMsg();
//            replyMsgVO.setId(replyMessage.getId());
//            replyMsgVO.setUid(replyMessage.getFromUid());
//            replyMsgVO.setType(replyMessage.getType());
//            replyMsgVO.setBody(MsgHandlerFactory.getStrategyNoNull(replyMessage.getType()).showReplyMsg(replyMessage));
//            User replyUser = userDao.getUserInfo(replyMessage.getFromUid());
//            replyMsgVO.setUsername(replyUser.getName());
//            replyMsgVO.setCanCallback(YesOrNoEnum.toStatus(Objects.nonNull(msg.getGapCount()) && msg.getGapCount() <= MessageAdapter.CAN_CALLBACK_GAP_COUNT));
//            replyMsgVO.setGapCount(msg.getGapCount());
//            resp.setReply(replyMsgVO);
//        }
        return resp;
    }

    @Override
    public Object showReplyMsg(Message msg) {
        return msg.getContent();
    }

    @Override
    public String showContactMsg(Message msg) {
        return "[音频]";
    }
}
