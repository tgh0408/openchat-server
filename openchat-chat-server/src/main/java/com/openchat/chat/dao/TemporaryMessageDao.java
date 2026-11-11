package com.openchat.chat.dao;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.stream.Collectors;

/** 路由两张原消息表；缓存由对应 DAO 持有，更新由实体版本控制。 */
@Repository
@RequiredArgsConstructor
public class TemporaryMessageDao {
    private final MessageDao messages;

    /** 房间类型决定消息表，缓存键同时绑定房间与消息标识。 */
    public record CacheKey(RoomTypeEnum roomType, Long roomId, Long msgId) {
        public CacheKey {
            if (roomType == null || roomId == null || roomId <= 0 || msgId == null || msgId <= 0) {
                throw new BusinessException("无效的消息缓存范围");
            }
        }

        private String scope() {
            return roomType.getType() + ":" + roomId;
        }
    }

    public static boolean supports(MessageTypeEnum type) {
        return MessageTypeEnum.equalsIn(type,
                MessageTypeEnum.TEMP, MessageTypeEnum.VIDEO, MessageTypeEnum.FILE);
    }

    public Message get(CacheKey key) {
        return messages.get(key.roomType(), key.roomId(), key.msgId());
    }

    public Map<CacheKey, Message> getBatch(List<CacheKey> keys) {
        Map<CacheKey, Message> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) return result;
        Map<String, List<CacheKey>> groups = keys.stream().collect(Collectors.groupingBy(
                CacheKey::scope, LinkedHashMap::new, Collectors.toList()));
        for (List<CacheKey> group : groups.values()) {
            CacheKey scope = group.getFirst();
            List<Long> ids = group.stream().map(CacheKey::msgId).distinct().toList();
            Map<Long, Message> found = messages.getBatch(scope.roomType(), scope.roomId(), ids);
            group.forEach(key -> result.put(key, found.get(key.msgId())));
        }
        return result;
    }

    public void deleteCache(CacheKey key) {
        messages.deleteCache(key.roomType(), key.roomId(), key.msgId());
    }

    public void deleteBatchCache(List<CacheKey> keys) {
        if (keys != null) keys.stream().distinct().forEach(this::deleteCache);
    }

    /** 调用方负责当前身份授权、历史边界和上传类型筛选。 */
    public List<Message> getUploadStates(RoomTypeEnum type, Long roomId, List<Long> ids) {
        if (ids == null || ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("上传状态每批最多 50 条");
        }
        List<CacheKey> keys = ids.stream().distinct().map(id -> new CacheKey(type, roomId, id)).toList();
        Map<CacheKey, Message> result = getBatch(keys);
        return keys.stream().map(result::get).filter(Objects::nonNull).toList();
    }

    public void invalidateMedia(RoomTypeEnum type, Long roomId, Long msgId) {
        deleteCache(new CacheKey(type, roomId, msgId));
    }

    public void invalidateAfterCommit(RoomTypeEnum type, Long roomId, Long msgId) {
        messages.invalidateAfterCommit(type, roomId, msgId);
    }

    public Message find(RoomTypeEnum type, Long roomId, Long uid, String clientMsgId) {
        return messages.find(type, roomId, uid, clientMsgId);
    }

    public Message getById(RoomTypeEnum type, Long id) {
        return messages.getMessageById(type, id);
    }

    public boolean update(RoomTypeEnum type, Message message) {
        return messages.updateById(type, message);
    }

    public List<Message> list(RoomTypeEnum type, List<Long> ids) {
        return messages.listByIds(type, ids);
    }

    public List<Message> listMedia(RoomTypeEnum type, Long roomId, List<Long> ids) {
        return messages.listMedia(type, roomId, ids);
    }
}
