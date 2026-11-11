package com.openchat.chat.service.abstracts;

import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.entity.msg.SoundMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.msg.SoundMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.chat.service.MessageResourceService;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.chat.service.TemporaryMessagePublisher;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.exception.BusinessException;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import com.openchat.common.service.MinioBucketService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** 语音条使用真实 WAV 对象和同事务可靠发布记录。 */
@Component
@RequiredArgsConstructor
public class SoundMsgHandler extends AbstractMsgHandler<SoundMsgReq> {
    private final MinIOTemplate minio;
    private final OssProperties properties;
    private final MessageResourceService resources;
    private final TemporaryMessageDao messages;
    private final RoomAccessService access;
    private final TemporaryMessagePublisher publisher;
    private final PlatformTransactionManager transactions;
    private final ContactDao contacts;
    private final MinioBucketService minioBucketService;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.SOUND;
    }

    @Override
    public void checkMsg(SoundMsgReq body, Long roomId, Long uid, RoomTypeEnum type) {
        String path = objectPath(body.getContent());
        String bucket = minioBucketService.currentBucket();
        String prefix = minioBucketService.prefix(bucket);
        if (uid == null || uid <= 0 || !path.equals(objectPath(body.getUploadAddress()))
                || !path.matches(Pattern.quote(prefix) + "sound/\\d{4}-\\d{2}/" + uid
                + "/[a-fA-F0-9-]{32,36}\\.wav")
                || body.getFileName() == null || !body.getFileName().matches("[^/\\\\\\p{Cntrl}]{1,251}\\.wav"))
            throw new BusinessException("只能发送本人上传的 WAV 语音");
        try {
            String key = path.substring(prefix.length());
            var object = minio.statObject(bucket, key);
            if (!Objects.equals(body.getFileSize(), object.size())
                    || !"audio/wav".equalsIgnoreCase(object.contentType()))
                throw new BusinessException("语音大小或格式不匹配");
            validateWave(minio.getObjectHeader(bucket, key, 44), object.size(), body.getSecond());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("语音尚未上传或存储不可用，请重试");
        }
    }

    /** 固定 PCM 格式使时长可以从真实对象长度验证，不执行客户端媒体或访问任意 URL。 */
    static void validateWave(byte[] header, long size, Integer second) {
        if (header == null || header.length < 44 || size < 32044 || size > 3840044
                || (size - 44) % 2 != 0 || second == null)
            throw new BusinessException("语音必须为 1 至 120 秒");
        ByteBuffer data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (!"RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVEfmt ".equals(new String(header, 8, 8, StandardCharsets.US_ASCII))
                || !"data".equals(new String(header, 36, 4, StandardCharsets.US_ASCII))
                || Integer.toUnsignedLong(data.getInt(4)) != size - 8 || data.getInt(16) != 16
                || data.getShort(20) != 1 || data.getShort(22) != 1
                || data.getInt(24) != 16000 || data.getInt(28) != 32000
                || data.getShort(32) != 2 || data.getShort(34) != 16
                || Integer.toUnsignedLong(data.getInt(40)) != size - 44
                || second != (int) Math.ceil((size - 44) / 32000.0))
            throw new BusinessException("语音文件头或时长无效");
    }

    private String objectPath(String value) {
        try {
            URI uri = URI.create(value);
            String endpoint = properties.getPublicEndpoint().replaceAll("/+$", "");
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || uri.getRawPath() == null || value.contains("%") || value.contains("..")
                    || value.startsWith("//")) throw new IllegalArgumentException();
            if (uri.isAbsolute() && !value.startsWith(endpoint + "/")) throw new IllegalArgumentException();
            String path = uri.isAbsolute() ? value.substring(endpoint.length()) : value;
            if (!path.startsWith("/")) path = "/" + path;
            OssUrlUtil.toPublicUrl(endpoint, path);
            return path;
        } catch (RuntimeException exception) {
            throw new BusinessException("语音地址无效");
        }
    }

    @Override
    public void sendMsg(ChatMessageReq request, RoomMessage room, Long uid) {
        if (request.getClientMsgId() == null || request.getClientMsgId().isBlank()
                || request.getClientMsgId().length() > 64)
            throw new BusinessException("语音必须提供 clientMsgId");
        // 工厂持有原实例，显式事务确保消息和可靠调用记录处于同一事务。
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            access.requireCanSend(uid, room.getRoomId());
            resources.requireOwner(uid);
            Message existing = messages.find(room.getRoomType(), room.getRoomId(), uid, request.getClientMsgId());
            if (existing != null) {
                if (existing.getType() == MessageTypeEnum.RECALL) {
                    publisher.publish(new MsgSendMessageDTO(existing.getId(), room.getRoomType(), true));
                    return;
                }
                SoundMsgReq body = validatedBody(request, room, uid);
                SoundMsgDTO extra = existing.getExtra() == null ? null : existing.getExtra().getSoundMsgDTO();
                if (existing.getType() != MessageTypeEnum.SOUND
                        || existing.getStatus() != com.openchat.common.domain.enums.YesOrNoEnum.NO || extra == null
                        || !Objects.equals(existing.getReplyMsgId(), request.getReplyId())
                        || !Objects.equals(existing.getContent(), objectPath(body.getContent()))
                        || !Objects.equals(extra.getSize(), body.getFileSize())
                        || !Objects.equals(extra.getSecond(), body.getSecond())
                        || !Objects.equals(extra.getFileName(), body.getFileName()))
                    throw new BusinessException("clientMsgId 已被其他消息使用");
                publisher.publish(new MsgSendMessageDTO(existing.getId(), room.getRoomType(), true));
                return;
            }
            long id = IdWorker.getId();
            super.checkAndSaveMsg(request, room, uid, id);
            if (room.getRoomType() == RoomTypeEnum.FILE_ASSISTANT) {
                contacts.createMissingContacts(room.getRoomId(), java.util.List.of(uid));
                contacts.changeContactStatus(room.getRoomId(), uid);
            }
            publisher.publish(new MsgSendMessageDTO(id, room.getRoomType()));
        });
    }

    @Override
    public void fillMsgContent(Message message, SoundMsgReq body, RoomTypeEnum type) {
        SoundMsgDTO sound = new SoundMsgDTO();
        sound.setFileName(body.getFileName());
        sound.setSize(body.getFileSize());
        sound.setSecond(body.getSecond());
        MessageExtra extra = new MessageExtra();
        extra.setSoundMsgDTO(sound);
        message.setExtra(extra);
        message.setContent(objectPath(body.getContent()));
    }

    @Override
    public Object showMsg(Message message) {
        TotalMsgResp response = new TotalMsgResp();
        response.setContent(OssUrlUtil.toPublicUrl(
                properties.getPublicEndpoint(), minioBucketService.currentBucket(), message.getContent()));
        response.setExtra(message.getExtra() == null ? null : message.getExtra().getSoundMsgDTO());
        return response;
    }

    @Override
    public Object showReplyMsg(Message message) {
        return "[语音]";
    }

    @Override
    public String showContactMsg(Message message) {
        return "[语音]";
    }
}
