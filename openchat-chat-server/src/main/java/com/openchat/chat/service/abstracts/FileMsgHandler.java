package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.FileMsgDTO;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.msg.FileMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import com.openchat.oss.OssExceptionDiagnostics;
import com.openchat.common.service.MinioBucketService;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileMsgHandler extends AbstractMsgHandler<FileMsgReq> {
    private final MinIOTemplate minIOTemplate;
    private final OssProperties ossProperties;
    private final MinioBucketService minioBucketService;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.FILE;
    }

    @Override
    public void checkMsg(FileMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
        if (uid == null || uid <= 0 || body.getFileName() == null || body.getFileName().isBlank()
                || body.getFileName().length() > 255 || body.getFileName().chars()
                .anyMatch(character -> character < 32 || character == 127
                        || character == '/' || character == '\\')) {
            throw new BusinessException("文件信息无效");
        }
        String path = objectPath(body.getContent());
        if (!path.equals(objectPath(body.getUploadAddress()))) {
            throw new BusinessException("文件上传与下载地址必须一致");
        }
        String bucket = minioBucketService.currentBucket();
        String prefix = minioBucketService.prefix(bucket);
        String pattern = Pattern.quote(prefix) + "file/\\d{4}-\\d{2}/" + uid
                + "/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.bin";
        if (!path.matches(pattern)) {
            throw new BusinessException("只能发送本人上传的文件");
        }
        try {
            StatObjectResponse object = minIOTemplate.statObject(bucket, path.substring(prefix.length()));
            if (object.size() < 1 || object.size() > 6L * 1024 * 1024 * 1024
                    || !Long.valueOf(object.size()).equals(body.getFileSize())) {
                throw new BusinessException("文件大小不匹配或超过 6 GiB");
            }
            if (!"application/octet-stream".equalsIgnoreCase(object.contentType())
                    || !Set.of(String.valueOf(MessageTypeEnum.FILE.getType())).equals(
                    object.userMetadata().get("openchat-target-type"))) {
                throw new BusinessException("文件必须通过文件分片上传接口提交");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("File upload validation failed: roomId={}, uid={}",
                    roomId, uid, OssExceptionDiagnostics.sanitized(exception));
            BusinessException error = new BusinessException("文件尚未上传或存储服务不可用，请重试");
            error.initCause(exception);
            throw error;
        }
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
//            //如果@用户不存在，userInfoCache 返回的map中依然存在该key，但是value为null，需要过滤掉再校验
//            long batchCount = batch.values().stream().filter(Objects::nonNull).count();
//            if (batchCount != atUidList.size()) {
//                throw new BusinessException("@用户不存在");
//            }
//        }
    }

    @Override
    public void fillMsgContent(Message message, FileMsgReq body, RoomTypeEnum roomTypeEnum) {
        // 文件上传时先请求OssController接口上传文件后将路径传回来
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        FileMsgDTO fileMsgDTO = new FileMsgDTO();
        fileMsgDTO.setFileName(body.getFileName());
        fileMsgDTO.setUpLoadPath(objectPath(body.getUploadAddress()));
        fileMsgDTO.setSize(body.getFileSize());
        extra.setFileMsg(fileMsgDTO);
        message.setExtra(extra);
        message.setContent(objectPath(body.getContent()));
    }

    @Override
    public Object showMsg(Message msg) {
        TotalMsgResp resp = new TotalMsgResp();
        resp.setContent(OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(), minioBucketService.currentBucket(), msg.getContent()));
        resp.setUrlContentMap(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getUrlContentMap).orElse(null));
        resp.setAtUidList(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getAtUidList).orElse(null));
        FileMsgDTO fileMsg = Optional.ofNullable(msg.getExtra())
                .orElse(new MessageExtra())
                .getFileMsg();
        if (fileMsg != null) {
            FileMsgDTO visible = new FileMsgDTO();
            visible.setFileName(fileMsg.getFileName());
            visible.setSize(fileMsg.getSize());
            resp.setExtra(visible);
        }
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
        return  msg.getContent();
    }

    @Override
    public String showContactMsg(Message msg) {
        return "[文件]";
    }

    /** 只接受公开地址或对象相对路径，拒绝把签名参数写入消息正文。 */
    private String objectPath(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || uri.getRawPath() == null || uri.getRawPath().contains("%")
                    || uri.getRawPath().contains("..") || value.startsWith("//")) {
                throw new IllegalArgumentException();
            }
            String endpoint = ossProperties.getPublicEndpoint().replaceAll("/+$", "");
            String path;
            if (uri.isAbsolute()) {
                if (!value.startsWith(endpoint + "/")) throw new IllegalArgumentException();
                path = value.substring(endpoint.length());
            } else {
                path = value.startsWith("/") ? value : "/" + value;
            }
            OssUrlUtil.toPublicUrl(endpoint, path);
            return path;
        } catch (RuntimeException exception) {
            throw new BusinessException("文件地址无效");
        }
    }
}
