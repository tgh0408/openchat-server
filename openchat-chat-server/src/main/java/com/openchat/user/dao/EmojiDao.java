package com.openchat.user.dao;

import com.openchat.user.domain.entity.Emoji;
import com.openchat.user.mapper.EmojiMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.common.service.LockService;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author TGH
 * @since 2024-11-28
 */
@Repository
public class EmojiDao {
    private final EmojiMapper emojiMapper;
    private final LockedRedisCache<Long, Emoji> cache;

    public EmojiDao(EmojiMapper emojiMapper, LockService locks) {
        this.emojiMapper = emojiMapper;
        this.cache = LockedRedisCache.create(locks,
                id -> "openchat:emoji:snapshot:{" + id + "}", this::loadCache,
                payload -> JsonUtils.toObj(payload, Emoji.class));
    }

    private Map<Long, Emoji> loadCache(List<Long> ids) {
        return emojiMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Emoji::getId, emoji -> emoji));
    }

    /** 读取表情实体，不向调用方暴露通用 Mapper。 */
    public Emoji get(Long id) {
        return cache.get(id);
    }

    /** 新建表情并保留数据库主键回填。 */
    public boolean save(Emoji emoji) {
        boolean saved = emojiMapper.insert(emoji) > 0;
        if (saved) cache.invalidateAfterCommit(emoji.getId());
        return saved;
    }

    /** 启动时仅加载去重索引所需字段，不将不完整实体写入按 ID 的缓存。 */
    public List<Emoji> listDigests() {
        return emojiMapper.selectList(new LambdaQueryWrapper<Emoji>().select(Emoji::getId, Emoji::getSha256));
    }
}
