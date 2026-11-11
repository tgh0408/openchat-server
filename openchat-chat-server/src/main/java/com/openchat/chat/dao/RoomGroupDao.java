package com.openchat.chat.dao;

import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.mapper.RoomGroupMapper;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 群聊房间表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-12-16
 */
@Repository
public class RoomGroupDao {
    private final RoomGroupMapper roomGroupMapper;
    private final LockedRedisCache<Long, RoomGroup> cache;

    public RoomGroupDao(RoomGroupMapper roomGroupMapper, LockService locks) {
        this.roomGroupMapper = roomGroupMapper;
        this.cache = LockedRedisCache.create(locks, this::cacheKey, this::loadCache,
                payload -> JsonUtils.toObj(payload, RoomGroup.class));
    }

    private String cacheKey(Long roomId) {
        return "openchat:room-group:snapshot:{" + roomId + "}";
    }

    private Map<Long, RoomGroup> loadCache(List<Long> roomIds) {
        List<RoomGroup> listByRoomIds = roomGroupMapper.selectList(new LambdaQueryWrapper<RoomGroup>()
                .in(RoomGroup::getRoomId, roomIds)
                .eq(RoomGroup::getDeleteStatus, YesOrNoEnum.NO));
        return listByRoomIds.stream().collect(Collectors.toMap(RoomGroup::getRoomId, group -> group));
    }

    public RoomGroup get(Long roomId) {
        return cache.get(roomId);
    }

    public RoomGroup get(Long roomId, String error) {
        return cache.get(roomId, error);
    }

    public Map<Long, RoomGroup> getBatch(List<Long> roomIds) {
        return cache.getBatch(roomIds);
    }

    public void deleteCache(Long roomId) {
        cache.deleteCache(roomId);
    }

    public void deleteBatchCache(List<Long> roomIds) {
        cache.deleteBatchCache(roomIds);
    }

    private void invalidateAfterCommit(Long roomId) {
        cache.invalidateAfterCommit(roomId);
    }

    public boolean save(RoomGroup group) {
        boolean saved = roomGroupMapper.insert(group) > 0;
        if (saved) invalidateAfterCommit(group.getRoomId());
        return saved;
    }

    /** 只写群资料，按读取版本及未解散状态原子更新，不回查或覆盖并发的新版本。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInfo(RoomGroup group) {
        if (group.getId() == null || group.getRoomId() == null || group.getVersion() == null) {
            throw new IllegalArgumentException("群资料更新缺少标识或版本");
        }
        RoomGroup changes = new RoomGroup();
        changes.setName(group.getName());
        changes.setExtJson(group.getExtJson());
        changes.setVersion(group.getVersion());
        changes.setUpdateTime(new Date());
        boolean updated = roomGroupMapper.update(changes, new LambdaUpdateWrapper<RoomGroup>()
                .eq(RoomGroup::getId, group.getId())
                .eq(RoomGroup::getRoomId, group.getRoomId())
                .eq(RoomGroup::getDeleteStatus, YesOrNoEnum.NO)) == 1;
        if (updated) {
            group.setVersion(changes.getVersion());
            group.setUpdateTime(changes.getUpdateTime());
            invalidateAfterCommit(group.getRoomId());
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(RoomGroup group) {
        RoomGroup stored = roomGroupMapper.selectById(group.getId());
        if (stored == null) return false;
        if (group.getRoomId() != null && !group.getRoomId().equals(stored.getRoomId())) {
            throw new IllegalArgumentException("群资料更新不能更换所属房间");
        }
        if (group.getVersion() == null) group.setVersion(stored.getVersion());
        boolean updated = roomGroupMapper.updateById(group) > 0;
        if (updated) invalidateAfterCommit(stored.getRoomId());
        return updated;
    }

    //在此表中Room也是唯一存在的
    @Transactional(rollbackFor = Exception.class)
    public void updateByRoomId(RoomGroup roomGroup) {
        List<RoomGroup> list = roomGroupMapper.selectList(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getRoomId, roomGroup.getRoomId()));
        if (list.size() > 1) {
            throw new BusinessException("room group 出现异常,请检查 : " + list);
        }else if (list.isEmpty()) {
            throw new BusinessException("需要需修改的房间号不存在 id: " + roomGroup.getRoomId());
        }
        RoomGroup stored = list.getFirst();
        roomGroup.setId(stored.getId());
        roomGroup.setVersion(stored.getVersion());
        //json字段只能主键更新否则报错
        updateById(roomGroup);
    }


    public void delRoomGroup(Long uid, Long roomId) {
        RoomGroup current = roomGroupMapper.selectOne(new LambdaQueryWrapper<RoomGroup>()
                .eq(RoomGroup::getUid, uid)
                .eq(RoomGroup::getRoomId, roomId)
                .eq(RoomGroup::getDeleteStatus, YesOrNoEnum.NO));
        if (current == null) return;
        current.setDeleteStatus(YesOrNoEnum.YES);
        if (roomGroupMapper.updateById(current) > 0) invalidateAfterCommit(roomId);
    }
}
