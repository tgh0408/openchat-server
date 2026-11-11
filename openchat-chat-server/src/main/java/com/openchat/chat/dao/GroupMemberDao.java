package com.openchat.chat.dao;

import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.enums.GroupRoleEnum;
import com.openchat.chat.mapper.GroupMemberMapper;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import com.openchat.chat.domain.event.GroupMembersChanged;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 群成员表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-12-16
 */
@Repository
@Slf4j
public class GroupMemberDao {
    private final GroupMemberMapper groupMemberMapper;
    private final ApplicationEventPublisher events;
    private final RoomDao roomDao;
    @Value("${openchat.chat.push.large-group-member-threshold:200}")
    private int largeGroupMemberThreshold;
    // room -> uids cache
    private final LockedRedisCache<Long, List<GroupMember>> cache;
    // uids -> room cache
    private final LockedRedisCache<Long, List<GroupMember>> uidRoomCache;

    public GroupMemberDao(GroupMemberMapper groupMemberMapper, ApplicationEventPublisher events,
                          RoomDao roomDao, LockService locks) {
        this.groupMemberMapper = groupMemberMapper;
        this.events = events;
        this.roomDao = roomDao;
        this.cache = LockedRedisCache.create(locks, this::cacheKey, this::loadCache,
                payload -> JsonUtils.toObj(payload, new TypeReference<List<GroupMember>>() {}));
        this.uidRoomCache = LockedRedisCache.create(locks, this::userIdRoomKey, this::loadUserRoomCache,
                payload -> JsonUtils.toObj(payload, new TypeReference<List<GroupMember>>() {}));
    }

    private String cacheKey(Long roomId) {
        if (roomId == null || roomId <= 0) throw new IllegalArgumentException("无效的群 ID");
        return "openchat:room-uid:snapshot:{" + roomId + "}";
    }

    private String userIdRoomKey(Long uid) {
        return "openchat:uid-room:snapshot:{" + uid + "}";
    }

    private Map<Long, List<GroupMember>> loadCache(List<Long> roomIds) {
        Map<Long, List<GroupMember>> result = new LinkedHashMap<>();
        List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .in(GroupMember::getRoomId, roomIds)
                .eq(GroupMember::getDeleteStatus, YesOrNoEnum.NO));
        for (Long roomId : roomIds) {
            result.put(roomId, members.stream()
                    .filter(member -> Objects.equals(member.getRoomId(), roomId))
                    .toList());
        }
        return result;
    }

    private Map<Long, List<GroupMember>> loadUserRoomCache(List<Long> uids) {
        Map<Long, List<GroupMember>> result = new LinkedHashMap<>();
        List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .in(GroupMember::getUid, uids)
                .eq(GroupMember::getDeleteStatus, YesOrNoEnum.NO));
        for (Long uid : uids) {
            result.put(uid, members.stream().filter(member -> Objects.equals(member.getUid(), uid)).toList());
        }
        return result;
    }

    public List<GroupMember> get(Long roomId) {
        return cache.get(roomId);
    }

    public Map<Long, List<GroupMember>> getBatch(List<Long> roomIds) {
        return cache.getBatch(roomIds);
    }

    public void deleteCache(Long roomId) {
        cache.deleteCache(roomId);
    }

    public List<String> getMemberUids(Long roomId) {
        return get(roomId).stream().map(member -> String.valueOf(member.getUid())).toList();
    }

    public Set<Long> getMemberUidSet(Long roomId) {
        return get(roomId).stream()
                .map(GroupMember::getUid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public GroupMember getMember(Long roomId, Long uid) {
        return get(roomId).stream().filter(member -> Objects.equals(member.getUid(), uid))
                .findFirst().orElse(null);
    }

    /** 由唯一索引处理新增或恢复；返回驱动报告的写入结果，不作为严格去重凭据。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean save(GroupMember member) {
        Room room = roomDao.get(member.getRoomId());
        if (room == null || room.getType() != RoomTypeEnum.GROUP) {
            throw new BusinessException("群聊房间不存在");
        }
        member.setDeleteStatus(YesOrNoEnum.NO);
        if (groupMemberMapper.insertOrRestore(member) <= 0) return false;
        roomDao.promoteToHotIfNeeded(member.getRoomId(), largeGroupMemberThreshold);
        events.publishEvent(new GroupMembersChanged(member.getRoomId(), member.getUid()));
        return true;
    }

    /** 重新入群降为普通成员，不恢复退出前的管理权限。 */
    public void restoreMember(GroupMember member) {
        member.setDeleteStatus(YesOrNoEnum.NO);
        member.setRole(GroupRoleEnum.NORMAL);
        int updated = groupMemberMapper.updateById(member);
        if (updated != 1) {
            throw new BusinessException("群成员状态已变化，请重试");
        }
        roomDao.promoteToHotIfNeeded(member.getRoomId(), largeGroupMemberThreshold);
        events.publishEvent(new GroupMembersChanged(member.getRoomId(), member.getUid()));
    }

    /** 带实体版本更新，防止旧状态覆盖，提交后复用缓存失效事件。 */
    public void removeMember(GroupMember member) {
        member.setDeleteStatus(YesOrNoEnum.YES);
        int updated = groupMemberMapper.updateById(member);
        if (updated != 1) throw new BusinessException("群成员状态已变化，请重试");
        events.publishEvent(new GroupMembersChanged(member.getRoomId(), member.getUid()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cleanCache(GroupMembersChanged event) {
        log.info("房间 [{}] 发生成员变化，清除缓存", event.roomId().toString());
        // 两个方向独立失效；一侧锁超时仍须尝试另一侧，并保留原始异常。
        RuntimeException failure = null;
        try {
            cache.deleteCache(event.roomId());
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            uidRoomCache.deleteCache(event.uid());
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    public void delUserByUserId(Long uid,Long roomId) {
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getUid, uid)
                        .eq(GroupMember::getRoomId, roomId)
                        .eq(GroupMember::getDeleteStatus, YesOrNoEnum.NO));
        if (member == null) return;
        member.setDeleteStatus(YesOrNoEnum.YES);
        if (groupMemberMapper.updateById(member) > 0) events.publishEvent(new GroupMembersChanged(roomId, member.getUid()));
    }

    /** 查询用户在哪些群组内**/
    public List<GroupMember> getUserRoom(Long uid) {
        return uidRoomCache.get(uid);
    }

    public boolean isSuperAdmin(@NotNull Long roomId, Long uid) {
        return GroupRoleEnum.isSuperAdmin(getMember(roomId, uid).getRole());
    }
}
