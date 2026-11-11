package com.openchat.user.dao;

import com.openchat.user.domain.entity.RoomFriend;
import com.openchat.user.mapper.RoomFriendMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.ApplicationEventPublisher;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 单聊房间表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-12-13
 */
@Repository
public class RoomFriendDao {
    private final RoomFriendMapper roomFriendMapper;
    private final ApplicationEventPublisher events;

    /** 同时定位房间快照和变更前后的好友组合键。 */
    public record RoomFriendChanged(Long roomId, String roomKey, String previousRoomKey) {}

    private final LockedRedisCache<Long, RoomFriend> roomFriendCache;
    private final LockedRedisCache<String, RoomFriend> pairCache;

    public RoomFriendDao(RoomFriendMapper roomFriendMapper, ApplicationEventPublisher events, LockService locks) {
        this.roomFriendMapper = roomFriendMapper;
        this.events = events;
        this.roomFriendCache = LockedRedisCache.create(locks,
                roomId -> "openchat:room-friend:snapshot:{" + roomId + "}",
                this::loadRoomCache, payload -> JsonUtils.toObj(payload, RoomFriend.class));
        this.pairCache = LockedRedisCache.create(locks,
                key -> "openchat:room-friend:pair:{" + key + "}",
                this::loadPairCache, payload -> JsonUtils.toObj(payload, RoomFriend.class));
    }

    private Map<Long, RoomFriend> loadRoomCache(List<Long> roomIds) {
        List<RoomFriend> roomFriends = roomFriendMapper.selectList(new LambdaQueryWrapper<RoomFriend>()
                .in(RoomFriend::getRoomId, roomIds));
        return roomFriends.stream().collect(Collectors.toMap(RoomFriend::getRoomId, Function.identity()));
    }


    private Map<String, RoomFriend> loadPairCache(List<String> keys) {
        return roomFriendMapper.selectList(new LambdaQueryWrapper<RoomFriend>()
                        .in(RoomFriend::getRoomKey, keys))
                .stream()
                .collect(Collectors.toMap(RoomFriend::getRoomKey, Function.identity()));
    }

    public RoomFriend get(Long roomId) {
        return roomFriendCache.get(roomId);
    }

    public RoomFriend get(String pairId) {
        return pairCache.get(pairId);
    }

    public RoomFriend get(String pairId, String error){
        return pairCache.get(pairId, error);
    }

    public RoomFriend get(Long roomId, String error) {
        return roomFriendCache.get(roomId, error);
    }

    public Map<Long, RoomFriend> getBatch(List<Long> roomIds) {
        return roomFriendCache.getBatch(roomIds);
    }

    public void deleteCache(Long roomId) {
        roomFriendCache.deleteCache(roomId);
    }

    public void deleteBatchCache(List<Long> roomIds) {
        roomFriendCache.deleteBatchCache(roomIds);
    }

    /** 不在提交后重新查库推导旧键，避免遗漏改键前的缓存。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cleanCache(RoomFriendChanged event) {
        RuntimeException failure = null;
        try {
            roomFriendCache.deleteCache(event.roomId());
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            pairCache.deleteBatchCache(Stream.of(event.previousRoomKey(), event.roomKey())
                    .filter(Objects::nonNull).distinct().toList());
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    public boolean save(RoomFriend friend) {
        boolean saved = roomFriendMapper.insert(friend) > 0;
        if (saved) events.publishEvent(new RoomFriendChanged(friend.getRoomId(), friend.getRoomKey(), null));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(RoomFriend friend) {
        RoomFriend stored = roomFriendMapper.selectById(friend.getId());
        if (stored == null) return false;
        if (friend.getRoomId() != null && !friend.getRoomId().equals(stored.getRoomId())) {
            throw new IllegalArgumentException("单聊资料更新不能更换所属房间");
        }
        if (friend.getVersion() == null) friend.setVersion(stored.getVersion());
        boolean updated = roomFriendMapper.updateById(friend) > 0;
        if (updated) events.publishEvent(new RoomFriendChanged(
                stored.getRoomId(), friend.getRoomKey(), stored.getRoomKey()));
        return updated;
    }

    public List<RoomFriend> getFriendRoomMap(Long uid, List<Long> friendUidList) {
        List<String> keys = friendUidList.stream()
                .map(friendUid -> Math.min(uid, friendUid) + "_" + Math.max(uid, friendUid))
                .distinct()
                .toList();
        Map<String, RoomFriend> rooms = pairCache.getBatch(keys);
        return keys.stream().map(rooms::get).toList();
    }
}
