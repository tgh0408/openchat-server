package com.openchat.chat.service.abstracts;


import cn.hutool.core.collection.CollectionUtil;
import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.FileMsgDTO;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.msg.ImgMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.common.exception.BusinessException;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import io.minio.StatObjectResponse;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.entity.User;
import com.openchat.user.service.UserService;
import com.openchat.common.service.MinioBucketService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.net.URI;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ImgMsgHandler extends AbstractMsgHandler<ImgMsgReq> {
    final RoomFriendDao roomFriendDao;
    final MessageDao messageDao;
    final UserService userService;
    final UserDao userDao;
    final MinIOTemplate minIOTemplate;
    final OssProperties ossProperties;
    final MinioBucketService minioBucketService;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.IMG;
    }

    @Override
    public void checkMsg(ImgMsgReq body, Long roomId, Long uid,RoomTypeEnum roomType) {
        if (uid == null || uid <= 0 || body.getFileName().chars()
                .anyMatch(character -> character < 32 || character == 127 || character == '/' || character == '\\')) {
            throw new BusinessException("图片信息无效");
        }
        String path = objectPath(body.getContent());
        if (!path.equals(objectPath(body.getUploadAddress()))) {
            throw new BusinessException("图片上传与下载地址不一致");
        }
        String bucket = minioBucketService.currentBucket();
        String prefix = minioBucketService.prefix(bucket);
        String pattern = Pattern.quote(prefix) + "img/\\d{4}-\\d{2}/" + uid
                + "/[a-fA-F0-9-]{32,36}\\.(?i:png|jpe?g|gif|bmp)";
        if (!path.matches(pattern)) {
            throw new BusinessException("只能发送本人上传的图片");
        }
        // 提交 MQ 前与事务落库时都检查真实对象，不访问客户端指定的任意域名。
        String objectName = path.substring(prefix.length());
        try {
            StatObjectResponse object = minIOTemplate.statObject(bucket, objectName);
            if (object.size() != body.getFileSize() || object.size() < 1 || object.size() > 52428800) {
                throw new BusinessException("图片大小不匹配或超过 50 MiB");
            }
            byte[] header = minIOTemplate.getObjectHeader(bucket, objectName, 32);
            String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String expectedType = switch (extension) {
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                default -> "image/jpeg";
            };
            boolean valid = switch (extension) {
                case "png" -> header.length >= 8 && Arrays.equals(Arrays.copyOf(header, 8),
                        new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                case "gif" -> header.length >= 6 && header[0] == 'G' && header[1] == 'I'
                        && header[2] == 'F' && header[3] == '8'
                        && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
                case "bmp" -> header.length >= 2 && header[0] == 'B' && header[1] == 'M';
                default -> header.length >= 3 && header[0] == (byte) 255
                        && header[1] == (byte) 216 && header[2] == (byte) 255;
            };
            if (!valid || !expectedType.equalsIgnoreCase(object.contentType())) {
                throw new BusinessException("图片格式无效，仅支持 JPG/JPEG、PNG、GIF、BMP");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("图片尚未上传或存储服务不可用，请重试");
        }
        //校验回复消息是否存在和是否在相同的聊天环境下
        if (Objects.nonNull(body.getReplyMsgId())) {
            Message replyMsg = messageDao.getById(roomType, body.getReplyMsgId());
            if (Objects.isNull(replyMsg)) {
                throw new BusinessException("回复消息不存在");
            }
            if (!Strings.CS.equals(replyMsg.getRoomId().toString(), roomId.toString())) {
                throw new BusinessException("只能回复相同会话内的消息");
            }
        }
        if (CollectionUtil.isNotEmpty(body.getAtUidList())) {
            //前端传入的@用户列表可能会重复，需要去重
            List<Long> atUidList = body.getAtUidList().stream().distinct().collect(Collectors.toList());
            Map<Long, User> batch = userService.getBatch(atUidList);
            //如果@用户不存在，userDao 返回的map中依然存在该key，但是value为null，需要过滤掉再校验
            long batchCount = batch.values().stream().filter(Objects::nonNull).count();
            if (batchCount != atUidList.size()) {
                throw new BusinessException("@用户不存在");
            }
        }
    }

    /** 只接受默认公开入口或其相对对象路径，签名和凭据不得落库。 */
    private String objectPath(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || uri.getRawPath() == null || uri.getRawPath().contains("%")
                    || uri.getRawPath().contains("..") || value.startsWith("//")) {
                throw new IllegalArgumentException();
            }
            String path;
            if (uri.isAbsolute()) {
                String endpoint = ossProperties.getPublicEndpoint().replaceAll("/+$", "");
                if (!value.startsWith(endpoint + "/")) throw new IllegalArgumentException();
                path = value.substring(endpoint.length());
            } else {
                path = value.startsWith("/") ? value : "/" + value;
            }
            OssUrlUtil.toPublicUrl(ossProperties.getPublicEndpoint(), path);
            return path;
        } catch (RuntimeException exception) {
            throw new BusinessException("图片地址无效");
        }
    }

    @Override
    public void fillMsgContent(Message message, ImgMsgReq body, RoomTypeEnum roomTypeEnum) {
        // 文件上传时先请求OssController接口上传文件后将路径传回来
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        FileMsgDTO fileMsgDTO = new FileMsgDTO();
        fileMsgDTO.setFileName(body.getFileName());
        fileMsgDTO.setUpLoadPath(objectPath(body.getUploadAddress()));
        fileMsgDTO.setSize(body.getFileSize());
        extra.setFileMsg(fileMsgDTO);
        message.setExtra(extra);
        extra.setAtUidList(body.getAtUidList());
        message.setContent(objectPath(body.getContent()));
    }


    @Override
    public Object showMsg(Message msg) {
        TotalMsgResp resp = new TotalMsgResp();
        resp.setContent(OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(), minioBucketService.currentBucket(), msg.getContent()));
        resp.setExtra(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getFileMsg).orElse(null));
        resp.setUrlContentMap(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getUrlContentMap).orElse(null));
        resp.setAtUidList(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getAtUidList).orElse(null));
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
        return OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(), minioBucketService.currentBucket(), msg.getContent());
    }

    @Override
    public String showContactMsg(Message msg) {
        return "[图片]";
    }
}
