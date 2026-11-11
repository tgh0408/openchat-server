package com.openchat.chat.cache;

import com.openchat.chat.domain.dto.MessageCacheKey;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;

import java.util.*;
import java.util.function.Function;

/**
 * 两张消息表共用的内部媒体缓存组件，不持有 Mapper 或执行数据库写入。
 * 表名仅用于缓存命名空间，数据库回源由 DAO 提供。
 */
public final class MessageRecordCache<E extends Message> extends LockedRedisCache<MessageCacheKey, E> {
    private final String table;
    private final Class<E> entityClass;
    private final Function<List<Long>, List<E>> loader;

    public MessageRecordCache(LockService locks, String table, Class<E> entityClass,
                              Function<List<Long>, List<E>> loader) {
        super(locks);
        this.table = table;
        this.entityClass = entityClass;
        this.loader = loader;
    }

    @Override
    protected long getTtlSeconds() {
        return 300;
    }

    @Override
    protected String getKey(MessageCacheKey key) {
        return "openchat:message:snapshot:{" + table + ":" + key.roomId() + "}:" + key.msgId();
    }

    @Override
    protected E decode(MessageCacheKey key, String payload) {
        return JsonUtils.toObj(payload, entityClass);
    }

    @Override
    protected Map<MessageCacheKey, E> load(List<MessageCacheKey> keys) {
        Set<MessageCacheKey> requested = new HashSet<>(keys);
        List<Long> ids = keys.stream().map(MessageCacheKey::msgId).distinct().toList();
        Map<MessageCacheKey, E> result = new LinkedHashMap<>();
        for (E message : loader.apply(ids)) {
            if (message == null || message.getId() == null || message.getRoomId() == null) continue;
            MessageCacheKey key = new MessageCacheKey(message.getRoomId(), message.getId());
            if (requested.contains(key) && eligible(key, message)) result.put(key, message);
        }
        return result;
    }

    @Override
    protected boolean eligible(MessageCacheKey key, E message) {
        return message != null && Objects.equals(key.msgId(), message.getId())
                && Objects.equals(key.roomId(), message.getRoomId())
                && message.getStatus() == YesOrNoEnum.NO;
    }

    @Override
    protected boolean cacheable(MessageCacheKey key, E message) {
        return message.getType() == MessageTypeEnum.VIDEO
                || message.getType() == MessageTypeEnum.FILE;
    }

    /** 调用前仍须校验当前身份、房间权限及历史删除边界。 */
    public E get(Long roomId, Long msgId) {
        return get(new MessageCacheKey(roomId, msgId));
    }

    public E get(Long roomId, Long msgId, String error) {
        return get(new MessageCacheKey(roomId, msgId), error);
    }

    public Map<Long, E> getBatch(Long roomId, List<Long> msgIds) {
        Map<Long, E> result = new LinkedHashMap<>();
        if (msgIds == null || msgIds.isEmpty()) return result;
        List<MessageCacheKey> keys = msgIds.stream().distinct()
                .map(id -> new MessageCacheKey(roomId, id)).toList();
        Map<MessageCacheKey, E> messages = getBatch(keys);
        keys.forEach(key -> result.put(key.msgId(), messages.get(key)));
        return result;
    }

    public void deleteCache(Long roomId, Long msgId) {
        deleteCache(new MessageCacheKey(roomId, msgId));
    }

    public void invalidateAfterCommit(Long roomId, Long msgId) {
        invalidateAfterCommit(new MessageCacheKey(roomId, msgId));
    }


}
