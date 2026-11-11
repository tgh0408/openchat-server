package com.openchat.chat.dao;

import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.mapper.RoomMapper;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.AssertUtil;
import com.openchat.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 房间单表 DAO；数据库操作与内部活跃快照缓存各自负责其一致性边界。 */
@Repository
public class RoomDao {
    private final RoomMapper roomMapper;
    private final LockedRedisCache<Long, Room> cache;

    public RoomDao(RoomMapper roomMapper, LockService locks) {
        this.roomMapper = roomMapper;
        this.cache = LockedRedisCache.create(locks,
                roomId -> "openchat:room:snapshot:{" + roomId + "}", this::loadCache,
                payload -> JsonUtils.toObj(payload, Room.class));
    }

    /** 新建房间，沿用 Mapper 的主键回填。 */
    public boolean save(Room room) {
        return roomMapper.insert(room) > 0;
    }

    public Room get(Long roomId) {
        return cache.get(roomId);
    }

    public Room get(Long roomId, String error) {
        return cache.get(roomId, error);
    }

    public void promoteToHotIfNeeded(Long roomId, int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("large group threshold must be positive");
        }
        if (roomMapper.promoteToHotIfNeeded(roomId, threshold) <= 0) {
            return;
        }
        cache.invalidateAfterCommit(roomId);
    }

    public Map<Long, Room> getBatch(List<Long> roomIds) {
        // LockService 按传入顺序获取锁，跨节点批量请求必须采用一致顺序。
        List<Long> ordered = roomIds == null ? List.of() : roomIds.stream().distinct().sorted().toList();
        Map<Long, Room> result = cache.getBatch(ordered);
        // 保留批量查询省略不存在房间的约定。
        result.values().removeIf(Objects::isNull);
        return result;
    }

    public void deleteCache(Long roomId) {
        cache.deleteCache(roomId);
    }

    public void deleteBatchCache(List<Long> roomIds) {
        cache.deleteBatchCache(roomIds);
    }

    public Room getOrCreateFileAssistant(Long uid) {
        roomMapper.createFileAssistantIfAbsent(uid);
        return roomMapper.selectOne(new LambdaQueryWrapper<Room>()
                .eq(Room::getType, RoomTypeEnum.FILE_ASSISTANT)
                .eq(Room::getOwnerUid, uid));
    }

    /** 仅供缓存回源，业务读取统一经过缓存入口。 */
    private Map<Long, Room> loadCache(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return Map.of();
        return roomMapper.selectByIds(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, room -> room));
    }

    /** 数据库按消息 ID 单调推进，提交后失效缓存，不将两者视为同一原子事务。 */
    public void refreshActiveTime(Long roomId, Long msgId, Date msgTime) {
        AssertUtil.isTrue(roomId != null && roomId > 0, "房间 ID 不能为空且必须大于 0");
        AssertUtil.isTrue(msgId != null && msgId > 0, "消息 ID 不能为空且必须大于 0");
        AssertUtil.isNonNull(msgTime, "消息创建时间不能为空");
        LambdaUpdateWrapper<Room> update = new LambdaUpdateWrapper<>();
        update.eq(Room::getId, roomId)
                .and(query -> query.isNull(Room::getLastMsgId).or().lt(Room::getLastMsgId, msgId))
                .set(Room::getLastMsgId, msgId)
                .set(Room::getActiveTime, msgTime);
        int updated = roomMapper.update(null, update);
        AssertUtil.isTrue(updated == 0 || updated == 1, "房间活跃状态更新行数异常");
        // 0 行也可能是上次 SQL 已成功、缓存失效失败后的重试，必须再次失效后才能确认待办。
        cache.invalidateAfterCommit(roomId);
    }
}
