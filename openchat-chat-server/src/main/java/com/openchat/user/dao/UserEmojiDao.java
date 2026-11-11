package com.openchat.user.dao;

import com.openchat.user.domain.entity.UserEmoji;
import com.openchat.user.domain.dto.EmojiInfoDTO;
import com.openchat.user.mapper.UserEmojiMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * 用户表情包 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2024-01-03
 */
@Repository
public class UserEmojiDao {
    private final UserEmojiMapper mapper;
    private final LockedRedisCache<Long, CollectionSnapshot> cache;

    /** 关系保留删除状态及版本；可见列表沿用联表结果，两者按用户一起失效。 */
    public record CollectionSnapshot(List<UserEmoji> collections, List<EmojiInfoDTO> details) {}

    public UserEmojiDao(UserEmojiMapper mapper, LockService locks) {
        this.mapper = mapper;
        this.cache = LockedRedisCache.create(locks,
                uid -> "openchat:user-emoji:snapshot:{" + uid + "}", this::loadCache,
                payload -> JsonUtils.toObj(payload, CollectionSnapshot.class));
    }

    private Map<Long, CollectionSnapshot> loadCache(List<Long> uids) {
        List<UserEmoji> collections = mapper.selectList(new LambdaQueryWrapper<UserEmoji>()
                .in(UserEmoji::getUid, uids)
                .orderByAsc(UserEmoji::getSort, UserEmoji::getEmojiId));
        Map<Long, CollectionSnapshot> snapshots = new LinkedHashMap<>();
        for (Long uid : uids) {
            snapshots.put(uid, new CollectionSnapshot(collections.stream()
                    .filter(collection -> Objects.equals(uid, collection.getUid())).toList(),
                    mapper.listByUid(uid)));
        }
        return snapshots;
    }

    /** 联表读取仍存在的收藏图片及排序。 */
    public List<EmojiInfoDTO> listDetailsByUid(Long uid) {
        return cache.get(uid).details();
    }

    /** 按唯一键新增或恢复收藏，恢复更新交给乐观锁插件。 */
    public int saveCollection(UserEmoji collection) {
        UserEmoji existing = getCollection(collection.getUid(), collection.getEmojiId());
        if (existing == null) {
            int inserted = mapper.insert(collection);
            if (inserted > 0) cache.invalidateAfterCommit(collection.getUid());
            return inserted;
        }
        if (existing.getDeleteStatus() == YesOrNoEnum.NO) return 0;
        existing.setSort(collection.getSort());
        existing.setDeleteStatus(YesOrNoEnum.NO);
        return updateCollection(existing);
    }

    /** 排序分配包含删除关系；事务内由缓存组件回源，不使用共享旧快照。 */
    public Long getMinimumSort(Long uid) {
        List<UserEmoji> collections = cache.get(uid).collections();
        return collections.isEmpty() ? null : collections.getFirst().getSort();
    }

    /** 按唯一键读取收藏关系，包含逻辑删除记录。 */
    public UserEmoji getCollection(Long uid, Long emojiId) {
        return cache.get(uid).collections().stream()
                .filter(collection -> Objects.equals(emojiId, collection.getEmojiId()))
                .findFirst().orElse(null);
    }

    /** 读取图片仍存在的首个收藏。 */
    public UserEmoji getFirstCollection(Long uid) {
        CollectionSnapshot snapshot = cache.get(uid);
        if (snapshot.details().isEmpty()) return null;
        Long emojiId = snapshot.details().getFirst().getImgId();
        return snapshot.collections().stream()
                .filter(collection -> collection.getDeleteStatus() == YesOrNoEnum.NO
                        && Objects.equals(emojiId, collection.getEmojiId()))
                .findFirst().orElse(null);
    }

    /** 保留旧排序条件，拒绝迟到更新。 */
    public int moveToFront(Long uid, Long emojiId, Long expected, Long sort) {
        UserEmoji current = getCollection(uid, emojiId);
        if (current == null || current.getDeleteStatus() != YesOrNoEnum.NO
                || !Objects.equals(current.getSort(), expected)) return 0;
        current.setSort(sort);
        return updateCollection(current);
    }

    /** 仅标记本人仍有效的收藏，不删除图片。 */
    public int deleteCollection(Long uid, Long emojiId) {
        UserEmoji current = getCollection(uid, emojiId);
        if (current == null || current.getDeleteStatus() != YesOrNoEnum.NO) return 0;
        current.setDeleteStatus(YesOrNoEnum.YES);
        return updateCollection(current);
    }

    public UserEmoji getUserEmoji(Long uid, Long emojiId) {
        UserEmoji collection = getCollection(uid, emojiId);
        return collection != null && collection.getDeleteStatus() == YesOrNoEnum.NO ? collection : null;
    }

    /** 写入仍以数据库乐观锁结果为准，提交后统一失效关系、列表和排序快照。 */
    private int updateCollection(UserEmoji collection) {
        int updated = mapper.updateById(collection);
        if (updated > 0) cache.invalidateAfterCommit(collection.getUid());
        return updated;
    }
}
