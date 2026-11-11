package com.openchat.user.dao;

import tools.jackson.core.type.TypeReference;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.domain.vo.req.CursorPageBaseReq;
import com.openchat.common.domain.vo.resp.CursorPageBaseResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import com.openchat.user.domain.dto.FriendApplySettings;
import com.openchat.user.domain.dto.FriendRemarkData;
import com.openchat.user.domain.entity.UserFriend;
import com.openchat.user.domain.dto.FriendPageRow;
import com.openchat.user.service.adapter.FriendPageCursor;
import com.openchat.user.service.adapter.NameSortKey;
import com.openchat.user.service.adapter.FriendRemarkAdapter;
import com.openchat.user.mapper.UserFriendMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;

/**
 * <p>
 * 用户联系人表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-12-10
 */
@Repository
public class UserFriendDao {
    private final UserFriendMapper userFriendMapper;
    private final ApplicationEventPublisher events;

    /** 好友关系是单向记录，写入或发现旧快照时按所属用户统一失效。 */
    public record UserFriendChanged(Long uid) {}

    private final LockedRedisCache<Long, List<UserFriend>> cache;

    public UserFriendDao(UserFriendMapper userFriendMapper, ApplicationEventPublisher events, LockService locks) {
        this.userFriendMapper = userFriendMapper;
        this.events = events;
        this.cache = LockedRedisCache.create(locks, this::cacheKey, this::loadCache,
                payload -> JsonUtils.toObj(payload, new TypeReference<>() {}));
    }

    private String cacheKey(Long uid) {
        return "openchat:user-friend:snapshot:{" + uid + "}";
    }

    private Map<Long, List<UserFriend>> loadCache(List<Long> uids) {
        List<UserFriend> userFriends = userFriendMapper.selectList(new LambdaQueryWrapper<UserFriend>()
                        .in(UserFriend::getUid,uids)
                .eq(UserFriend::getDeleteStatus, YesOrNoEnum.NO));
        Map<Long,List<UserFriend>> userFriendMap = new HashMap<>();
        for (Long uid : uids) {
            List<UserFriend> friends = userFriends.stream().filter(f -> f.getUid().equals(uid)).toList();
            userFriendMap.put(uid, friends);
        }
        return userFriendMap;
    }

    public UserFriend get(Long uid, Long targetUid) {
        return cache.get(uid).stream()
                .filter(f -> f.getDeleteStatus() == YesOrNoEnum.NO && f.getFriendUid().equals(targetUid))
                .findFirst().orElse(null);
    }

    public UserFriend get(Long uid, Long targetUid, String error) {
        return Optional.ofNullable(get(uid, targetUid)).orElseThrow(() -> new BusinessException(error));
    }

    /** 关系与拉黑状态单条查询，包含已删除记录，不读取或新增关系缓存。 */
    public UserFriend getRelation(Long uid, Long targetUid) {
        return userFriendMapper.selectOne(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUid, uid)
                .eq(UserFriend::getFriendUid, targetUid));
    }

    /** 批量好友检查复用当前账号关系缓存，不另发数据库查询。 */
    public List<UserFriend> getByFriends(Long uid, List<Long> uidList) {
        Set<Long> targets = new HashSet<>(uidList);
        return cache.get(uid).stream()
                .filter(friend -> friend.getDeleteStatus() == YesOrNoEnum.NO
                        && targets.contains(friend.getFriendUid())).toList();
    }

    public boolean save(UserFriend friend) {
        friend.setNicknameSortKey(NameSortKey.build(friend.getNickname()));
        boolean saved = userFriendMapper.insert(friend) == 1;
        if (saved) events.publishEvent(new UserFriendChanged(friend.getUid()));
        return saved;
    }

    /** 在审批事务中按 UID 升序补齐双向关系，状态及版本条件拒绝旧快照，唯一索引拒绝重复创建。 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureMutualFriend(Long uid, Long friendUid) {
        if (uid == null || friendUid == null || uid <= 0 || friendUid <= 0 || uid.equals(friendUid)) {
            throw new BusinessException("好友对象无效");
        }
        Long first = Math.min(uid, friendUid);
        Long second = Math.max(uid, friendUid);
        ensureFriend(first, second);
        ensureFriend(second, first);
    }

    /** 活跃关系只推进版本；删除关系条件恢复，真正缺失时插入，不用旁路查询区分删除行。 */
    private void ensureFriend(Long uid, Long friendUid) {
        UserFriend friend = get(uid, friendUid);
        if (friend != null) {
            if (friend.getId() == null || friend.getVersion() == null
                    || friend.getBlackStatus() != YesOrNoEnum.NO) {
                throw new BusinessException("好友关系已变化，请刷新后重试");
            }
            int changed = userFriendMapper.update(null, new LambdaUpdateWrapper<UserFriend>()
                    .set(UserFriend::getVersion, friend.getVersion() + 1)
                    .set(UserFriend::getUpdateTime, new Date())
                    .eq(UserFriend::getId, friend.getId())
                    .eq(UserFriend::getUid, uid)
                    .eq(UserFriend::getFriendUid, friendUid)
                    .eq(UserFriend::getVersion, friend.getVersion())
                    .eq(UserFriend::getBlackStatus, YesOrNoEnum.NO));
            if (changed != 1) throw new BusinessException("好友关系已变化，请刷新后重试");
        } else {
            UserFriend deleted = getRelation(uid, friendUid);
            int restored = 0;
            if (deleted != null && deleted.getId() != null && deleted.getVersion() != null
                    && deleted.getDeleteStatus() == YesOrNoEnum.YES
                    && deleted.getBlackStatus() == YesOrNoEnum.NO) {
                UserFriend patch = UserFriend.builder()
                        .id(deleted.getId())
                        .version(deleted.getVersion())
                        .deleteStatus(YesOrNoEnum.NO)
                        .build();
                restored = userFriendMapper.update(patch, new LambdaUpdateWrapper<UserFriend>()
                        .eq(UserFriend::getId, deleted.getId())
                        .eq(UserFriend::getUid, uid)
                        .eq(UserFriend::getFriendUid, friendUid)
                        .eq(UserFriend::getDeleteStatus, YesOrNoEnum.YES)
                        .eq(UserFriend::getBlackStatus, YesOrNoEnum.NO)
                        .set(UserFriend::getUpdateTime, new Date()));
            }
            if (restored == 0) {
                UserFriend created = UserFriend.builder()
                        .uid(uid)
                        .friendUid(friendUid)
                        .deleteStatus(YesOrNoEnum.NO)
                        .blackStatus(YesOrNoEnum.NO)
                        .version(0)
                        .build();
                try {
                    if (!save(created)) throw new BusinessException("创建好友关系失败");
                } catch (DuplicateKeyException exception) {
                    // 已拉黑的删除行或并发新建均不能当作成功，也不查新版本自动重试。
                    throw new BusinessException("好友关系已变化，请刷新后重试");
                }
                return;
            }
            if (restored != 1) throw new BusinessException("好友关系数据异常，请联系管理员");
        }
        events.publishEvent(new UserFriendChanged(uid));
    }

    /** 恢复逻辑删除的本人方向，只对本人 user_friend 记录做乐观锁更新。 */
    public boolean restoreRetainedFriend(Long uid, UserFriend retained,
                                         FriendApplySettings settings) {
        UserFriend mine = getRelation(uid, retained.getUid());
        if (mine == null || mine.getId() == null || mine.getVersion() == null
                || mine.getDeleteStatus() != YesOrNoEnum.YES
                || mine.getBlackStatus() != YesOrNoEnum.NO) {
            return false;
        }
        String sortKey = NameSortKey.build(settings.getRemark());
        UserFriend updated = UserFriend.builder()
                .id(mine.getId())
                .version(mine.getVersion())
                .deleteStatus(YesOrNoEnum.NO)
                .nickname(sortKey == null ? null : settings.getRemark())
                .nicknameSortKey(sortKey)
                .tags(new FriendRemarkData(settings.getTags(), List.of(), "", List.of()))
                .updateTime(new Date())
                .build();
        int changed = userFriendMapper.update(updated, new LambdaUpdateWrapper<UserFriend>()
                .set(UserFriend::getNickname, updated.getNickname())
                .set(UserFriend::getNicknameSortKey, updated.getNicknameSortKey())
                .eq(UserFriend::getId, updated.getId()));
        // 只做提交后的共享缓存失效；回滚不广播，缓存不是写入成功的依据。
        if (changed == 1) {
            events.publishEvent(new UserFriendChanged(uid));
            events.publishEvent(new UserFriendChanged(retained.getUid()));
        }
        return changed == 1;
    }

    /** 沿用读取时的版本，交给插件校验及递增，不补查新版本或自动重试。 */
    public boolean updateById(UserFriend friend) {
        if (friend == null || friend.getId() == null || friend.getVersion() == null
                || friend.getUid() == null || friend.getFriendUid() == null) {
            throw new BusinessException("好友关系版本无效，请刷新后重试");
        }
        if (friend.getNickname() != null) {
            friend.setNicknameSortKey(NameSortKey.build(friend.getNickname()));
        }
        boolean updated = userFriendMapper.updateById(friend) == 1;
        if (updated) {
            events.publishEvent(new UserFriendChanged(friend.getUid()));
        }
        return updated;
    }

    /** 备注与排序键原子更新；null 或空白均清空备注，不影响其他关系字段。 */
    public boolean updateNickname(Long uid, Long friendUid, String nickname) {
        String key = NameSortKey.build(nickname);
        UserFriend friend = get(uid, friendUid);
        if (friend == null) return false;
        friend.setNickname(key == null ? null : nickname.strip());
        friend.setNicknameSortKey(key);
        boolean updated = userFriendMapper.updateById(friend) > 0;
        if (updated) events.publishEvent(new UserFriendChanged(uid));
        return updated;
    }

    /** 只写指定方向的备注和标签，显式清空空备注，版本条件避免旧快照覆盖并发变更。 */
    public boolean applySettings(Long uid, Long friendUid, FriendApplySettings settings) {
        String key = NameSortKey.build(settings.getRemark());
        UserFriend friend = get(uid, friendUid, "找不到好友关系");
        if (friend.getId() == null || friend.getVersion() == null) {
            throw new BusinessException("好友关系版本无效，请刷新后重试");
        }
        UserFriend patch = UserFriend.builder()
                .id(friend.getId())
                .version(friend.getVersion())
                .tags(FriendRemarkAdapter.withTags(friend, settings.getTags()))
                .build();
        boolean updated = userFriendMapper.update(patch, new LambdaUpdateWrapper<UserFriend>()
                .set(UserFriend::getNickname, key == null ? null : settings.getRemark())
                .set(UserFriend::getNicknameSortKey, key)
                .set(UserFriend::getVersion, friend.getVersion() + 1)
                .set(UserFriend::getUpdateTime, new Date())
                .eq(UserFriend::getId, friend.getId())
                .eq(UserFriend::getUid, uid)
                .eq(UserFriend::getFriendUid, friendUid)
                .eq(UserFriend::getVersion, friend.getVersion())
                .eq(UserFriend::getBlackStatus, YesOrNoEnum.NO)) == 1;
        if (updated) events.publishEvent(new UserFriendChanged(uid));
        return updated;
    }

    /** 传入独立更新实体及原版本，版本校验和递增由乐观锁插件处理。 */
    public boolean updateRemark(UserFriend updated) {
        updated.setUpdateTime(new Date());
        int changed = userFriendMapper.update(updated, new LambdaUpdateWrapper<UserFriend>()
                // 实体默认跳过 null，清空备注及排序键时显式置空。
                .set(updated.getNickname() == null, UserFriend::getNickname, null)
                .set(updated.getNicknameSortKey() == null, UserFriend::getNicknameSortKey, null)
                .eq(UserFriend::getId, updated.getId())
                .eq(UserFriend::getUid, updated.getUid())
                .eq(UserFriend::getFriendUid, updated.getFriendUid())
                .eq(UserFriend::getDeleteStatus, YesOrNoEnum.NO));
        events.publishEvent(new UserFriendChanged(updated.getUid()));
        return changed == 1;
    }

    /** 提交后失效共享快照；无事务写入立即失效，回滚不处理。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cleanCache(UserFriendChanged event) {
        cache.deleteCache(event.uid());
    }

    public CursorPageBaseResp<FriendPageRow> getFriendPage(Long uid, CursorPageBaseReq request) {
        int size = request.getPageSize();
        FriendPageCursor cursor = FriendPageCursor.decode(uid, request.getCursor());
        // 多取一条判断末页，游标仍取实际返回的最后一条关系，不能跳过探测记录。
        List<FriendPageRow> rows = userFriendMapper.getFriendPage(uid, cursor == null ? null : cursor.sortKey(),
                cursor == null ? null : cursor.id(), size + 1);
        List<FriendPageRow> list = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
        String nextCursor;
        if (!list.isEmpty()) {
            FriendPageRow last = list.getLast();
            nextCursor = FriendPageCursor.encode(uid, last.getSortKey(), last.getId());
        }else {
            nextCursor = null;
        }
        return new CursorPageBaseResp<>(nextCursor, request.isFirstPage(), rows.size() <= size, list);
    }


}
