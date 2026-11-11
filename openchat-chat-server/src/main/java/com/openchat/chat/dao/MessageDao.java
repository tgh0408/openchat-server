package com.openchat.chat.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.chat.cache.MessageRecordCache;
import com.openchat.chat.domain.dto.MessageCacheKey;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.MessageUser;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessagePageReq;
import com.openchat.chat.mapper.MessageMapper;
import com.openchat.chat.mapper.MessageUserMapper;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.domain.vo.resp.CursorPageBaseResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.service.LockService;
import com.openchat.common.utils.CursorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 消息数据访问入口，按房间类型路由到 message 或 message_user。
 */
@Repository
public class MessageDao {
    private final MessageMapper messageMapper;
    private final MessageUserMapper messageUserMapper;
    private final MessageRecordCache<Message> cache;
    private final MessageRecordCache<MessageUser> userCache;
    @Autowired
    public MessageDao(MessageMapper messageMapper, MessageUserMapper messageUserMapper, LockService locks) {
        this.messageMapper = messageMapper;
        this.messageUserMapper = messageUserMapper;
        this.cache = new MessageRecordCache<>(locks, "message", Message.class, messageMapper::selectByIds);
        this.userCache = new MessageRecordCache<>(locks, "message_user", MessageUser.class, messageUserMapper::selectByIds);
    }

    /** 按房间类型读取消息缓存。 */
    public Message get(RoomTypeEnum roomType, Long roomId, Long msgId) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> cache.get(roomId, msgId);
            case FRIEND, FILE_ASSISTANT -> userCache.get(roomId, msgId);
        };
    }

    /** 按房间类型批量读取消息缓存。 */
    public Map<Long, Message> getBatch(RoomTypeEnum roomType, Long roomId, List<Long> msgIds) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> copyMessages(cache.getBatch(roomId, msgIds));
            case FRIEND, FILE_ASSISTANT -> copyMessages(userCache.getBatch(roomId, msgIds));
        };
    }

    /** 按房间类型批量读取带房间归属的消息缓存。 */
    public Map<MessageCacheKey, Message> getBatch(RoomTypeEnum roomType, List<MessageCacheKey> keys) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> copyMessagesByKey(cache.getBatch(keys));
            case FRIEND, FILE_ASSISTANT -> copyMessagesByKey(userCache.getBatch(keys));
        };
    }

    /** 失效单条消息缓存。 */
    public void deleteCache(RoomTypeEnum roomType, Long roomId, Long msgId) {
        switch (requireRoomType(roomType)) {
            case GROUP -> cache.deleteCache(roomId, msgId);
            case FRIEND, FILE_ASSISTANT -> userCache.deleteCache(roomId, msgId);
        }
    }

    /** 失效一批消息缓存。 */
    public void deleteBatchCache(RoomTypeEnum roomType, List<MessageCacheKey> keys) {
        if (keys == null || keys.isEmpty()) return;
        switch (requireRoomType(roomType)) {
            case GROUP -> cache.deleteBatchCache(keys);
            case FRIEND, FILE_ASSISTANT -> userCache.deleteBatchCache(keys);
        }
    }

    /** 在事务提交后失效对应消息缓存。 */
    public void invalidateAfterCommit(RoomTypeEnum roomType, Long roomId, Long msgId) {
        switch (requireRoomType(roomType)) {
            case GROUP -> cache.invalidateAfterCommit(roomId, msgId);
            case FRIEND, FILE_ASSISTANT -> userCache.invalidateAfterCommit(roomId, msgId);
        }
    }

    /** 按房间类型读取单条消息，不使用缓存。 */
    public Message getById(RoomTypeEnum roomType, Long msgId) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> messageMapper.selectById(msgId);
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.selectById(msgId);
        };
    }

    /** 不确定房间类型时检查两张消息表。 */
    public boolean exists(Long msgId) {
        return messageMapper.selectById(msgId) != null
                || messageUserMapper.selectById(msgId) != null;
    }

    /** 按房间类型批量读取消息，不使用缓存。 */
    public List<Message> listByIds(RoomTypeEnum roomType, List<Long> msgIds) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> new ArrayList<>(messageMapper.selectByIds(msgIds));
            case FRIEND, FILE_ASSISTANT -> new ArrayList<>(messageUserMapper.selectByIds(msgIds));
        };
    }

    /** 按房间类型保存消息。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean save(RoomTypeEnum roomType, Message message) {
        RoomTypeEnum type = requireRoomType(roomType);
        if (message.getVersion() == null) message.setVersion(1);
        int affected = switch (type) {
            case GROUP -> messageMapper.insert(message);
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.insert(asMessageUser(message));
        };
        return affected > 0;
    }

    /** 按房间类型查询客户端消息。 */
    public Message find(RoomTypeEnum roomType, Long roomId, Long uid, String clientMsgId) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getRoomId, roomId)
                    .eq(Message::getFromUid, uid)
                    .eq(Message::getClientMsgId, clientMsgId)
                    .last("LIMIT 1"));
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.selectOne(new LambdaQueryWrapper<MessageUser>()
                    .eq(MessageUser::getRoomId, roomId)
                    .eq(MessageUser::getFromUid, uid)
                    .eq(MessageUser::getClientMsgId, clientMsgId)
                    .last("LIMIT 1"));
        };
    }

    /** 按房间类型按主键读取消息。 */
    public Message getMessageById(RoomTypeEnum roomType, Long msgId) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> messageMapper.selectById(msgId);
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.selectById(msgId);
        };
    }

    /** 按房间类型更新消息并在提交后失效缓存。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(RoomTypeEnum roomType, Message message) {
        RoomTypeEnum type = requireRoomType(roomType);
        Message stored = getMessageById(type, message.getId());
        if (stored == null) return false;
        if (message.getRoomId() != null && !Objects.equals(message.getRoomId(), stored.getRoomId())) {
            throw new IllegalArgumentException("消息更新不能更换所属房间");
        }
        if (message.getVersion() == null) message.setVersion(stored.getVersion());
        int affected = switch (type) {
            case GROUP -> messageMapper.updateById(message);
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.updateById(asMessageUser(message));
        };
        if (affected > 0) {
            invalidateAfterCommit(type, stored.getRoomId(), stored.getId());
        }
        return affected > 0;
    }

    /** 按房间类型分页读取消息。 */
    public CursorPageBaseResp<Message> getCursorPage(
            RoomTypeEnum roomType, Long roomId, ChatMessagePageReq request, Long lastMsgId, Long lastDeleteId) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> CursorUtils.getCursorPageByMysql(messageMapper, request, wrapper -> {
                wrapper.eq(Message::getRoomId, roomId);
                wrapper.eq(Message::getStatus, YesOrNoEnum.NO);
                wrapper.le(lastMsgId != null, Message::getId, lastMsgId);
                wrapper.gt(lastDeleteId != null, Message::getId, lastDeleteId);
            }, Message::getId);
            case FRIEND, FILE_ASSISTANT -> {
                CursorPageBaseResp<MessageUser> page = CursorUtils.getCursorPageByMysql(
                        messageUserMapper, request, wrapper -> {
                            wrapper.eq(MessageUser::getRoomId, roomId);
                            wrapper.eq(MessageUser::getStatus, YesOrNoEnum.NO);
                            wrapper.le(lastMsgId != null, MessageUser::getId, lastMsgId);
                            wrapper.gt(lastDeleteId != null, MessageUser::getId, lastDeleteId);
                        }, MessageUser::getId);
                yield CursorPageBaseResp.init(page, page.getList().stream()
                        .map(message -> (Message) message).toList());
            }
        };
    }

    /** 查询指定房间真实存在的最后一条消息。 */
    public Message getLastMsgId(RoomTypeEnum roomType, Long roomId) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getRoomId, roomId)
                    .orderByDesc(Message::getId)
                    .last("LIMIT 1"));
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.selectOne(new LambdaQueryWrapper<MessageUser>()
                    .eq(MessageUser::getRoomId, roomId)
                    .orderByDesc(MessageUser::getId)
                    .last("LIMIT 1"));
        };
    }

    /** 统计指定消息表中的未读消息。 */
    public Integer getUnReadCount(RoomTypeEnum roomType, Long roomId, Long uid, long readId, long lastMsgId) {
        if (readId >= lastMsgId) return 0;
        return switch (requireRoomType(roomType)) {
            case GROUP -> messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                    .eq(Message::getRoomId, roomId)
                    .eq(Message::getStatus, YesOrNoEnum.NO)
                    .ne(Message::getFromUid, uid)
                    .gt(Message::getId, readId)
                    .le(Message::getId, lastMsgId)).intValue();
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.selectCount(new LambdaQueryWrapper<MessageUser>()
                    .eq(MessageUser::getRoomId, roomId)
                    .eq(MessageUser::getStatus, YesOrNoEnum.NO)
                    .ne(MessageUser::getFromUid, uid)
                    .gt(MessageUser::getId, readId)
                    .le(MessageUser::getId, lastMsgId)).intValue();
        };
    }

    /**
     * 统计引用两端之间的可见消息；固定上界避免把并发产生的更晚消息计入。
     */
    public Long countReplyGap(RoomTypeEnum roomType, Long roomId, Long replyId, Long msgId) {
        if (roomId == null || roomId <= 0 || replyId == null || replyId <= 0
                || msgId == null || replyId >= msgId) {
            throw new BusinessException("无效的引用消息区间");
        }
        return switch (requireRoomType(roomType)) {
            case GROUP -> messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                    .eq(Message::getRoomId, roomId)
                    .eq(Message::getStatus, YesOrNoEnum.NO)
                    .gt(Message::getId, replyId)
                    .lt(Message::getId, msgId));
            case FRIEND, FILE_ASSISTANT -> messageUserMapper.selectCount(new LambdaQueryWrapper<MessageUser>()
                    .eq(MessageUser::getRoomId, roomId)
                    .eq(MessageUser::getStatus, YesOrNoEnum.NO)
                    .gt(MessageUser::getId, replyId)
                    .lt(MessageUser::getId, msgId));
        };
    }

    /** 查询指定房间中的媒体消息，不使用缓存。 */
    public List<Message> listMedia(RoomTypeEnum roomType, Long roomId, List<Long> ids) {
        return switch (requireRoomType(roomType)) {
            case GROUP -> new ArrayList<>(messageMapper.selectList(new LambdaQueryWrapper<Message>()
                    .eq(Message::getRoomId, roomId)
                    .eq(Message::getStatus, YesOrNoEnum.NO)
                    .in(Message::getType, mediaTypes())
                    .in(Message::getId, ids)));
            case FRIEND, FILE_ASSISTANT -> new ArrayList<>(messageUserMapper.selectList(
                    new LambdaQueryWrapper<MessageUser>()
                            .eq(MessageUser::getRoomId, roomId)
                    .eq(MessageUser::getStatus, YesOrNoEnum.NO)
                            .in(MessageUser::getType, mediaTypes())
                            .in(MessageUser::getId, ids)));
        };
    }

    private static List<MessageTypeEnum> mediaTypes() {
        return List.of(
                MessageTypeEnum.TEMP,
                MessageTypeEnum.VIDEO,
                MessageTypeEnum.FILE);
    }

    private static RoomTypeEnum requireRoomType(RoomTypeEnum roomType) {
        if (roomType == null) throw new BusinessException("无效的消息房间类型");
        return roomType;
    }

    private static MessageUser asMessageUser(Message message) {
        if (message instanceof MessageUser messageUser) return messageUser;
        throw new IllegalArgumentException("message_user 必须使用 MessageUser 实体");
    }

    private static Map<Long, Message> copyMessages(Map<Long, ? extends Message> source) {
        return new LinkedHashMap<>(source);
    }

    private static Map<MessageCacheKey, Message> copyMessagesByKey(
            Map<MessageCacheKey, ? extends Message> source) {
        return new LinkedHashMap<>(source);
    }
}
