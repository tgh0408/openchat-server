package com.openchat.chat.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.dto.RoomActivityUpdate;
import com.openchat.chat.refresh.RoomActivityBuffer;
import com.openchat.common.constant.RedisKey;
import com.openchat.common.utils.AssertUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Date;

/**
 * 按房间合并活跃状态，并定时将到期快照写入数据库。
 * 消息消费只负责可靠入队；落库允许短暂延迟，不合并消息推送或已读游标更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomActivityService {
    private final RoomActivityBuffer buffer;
    private final RoomDao roomDao;
    private final RedissonClient redissonClient;

    // 同一房间的消息合并窗口，单位为毫秒。
    @Value("${openchat.chat.room-activity.window-ms:1000}")
    private long windowMillis = 1000;
    // 限制每轮处理的房间数，避免单次任务处理过多积压。
    @Value("${openchat.chat.room-activity.batch-size:500}")
    private int batchSize = 500;

    @PostConstruct
    public void validateConfiguration() {
        AssertUtil.isTrue(windowMillis > 0, "房间活跃状态合并窗口必须大于 0 毫秒");
        AssertUtil.isTrue(batchSize > 0, "房间活跃状态单批处理数量必须大于 0");
    }

    public void record(Long roomId, Long msgId, Date msgTime) {
        AssertUtil.isTrue(roomId != null && roomId > 0, "房间 ID 不能为空且必须大于 0");
        AssertUtil.isTrue(msgId != null && msgId > 0, "消息 ID 不能为空且必须大于 0");
        AssertUtil.isNonNull(msgTime, "消息创建时间不能为空");
        // 入队异常必须传递给 MQ 消费器，避免消息被提前确认而无法重试。
        buffer.record(new RoomActivityUpdate(roomId, msgId, msgTime.getTime()), windowMillis);
    }

    /**
     * 多实例共用一把分布式锁，串行处理到期待办；未抢到锁的实例跳过本轮。
     */
    public void flush() {
        RLock lock = redissonClient.getLock(RedisKey.getKey(RedisKey.ROOM_ACTIVITY_FLUSH_LOCK));
        if (!lock.tryLock()) {
            return;
        }
        try {
            for (Long roomId : buffer.dueRooms(batchSize)) {
                // 锁失效后停止处理后续房间，避免继续与其他实例同时落库。
                if (!lock.isHeldByCurrentThread()) {
                    return;
                }
                try {
                    RoomActivityUpdate update = buffer.get(roomId);
                    roomDao.refreshActiveTime(roomId, update.msgId(), new Date(update.msgTime()));
                    // 数据库更新和缓存失效都成功后才能确认，脚本会保留落库期间到达的新消息。
                    buffer.acknowledge(update, windowMillis);
                } catch (Exception e) {
                    // 单个房间失败不阻断本批其他房间，保留待办供后续轮次重试。
                    log.error("房间活跃状态落库失败，房间 ID：{}，已保留待办等待重试", roomId, e);
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
