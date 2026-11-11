package com.openchat.chat.refresh;

import com.openchat.chat.domain.dto.RoomActivityUpdate;
import com.openchat.common.constant.RedisKey;
import com.openchat.common.utils.RedisUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 房间活跃状态的 Redis 待写缓冲区。
 * 每个房间用 Hash 保存最新消息快照，用 ZSet 的分数记录窗口到期时间。
 * 待办不是普通缓存，不设置过期时间，只有落库并完成房间缓存失效后才能确认。
 */
@Component
public class RoomActivityBuffer {
    private static final String PENDING_KEY = RedisKey.getKey(RedisKey.ROOM_ACTIVITY_PENDING);
    private static final DefaultRedisScript<Long> RECORD = script("record");
    private static final DefaultRedisScript<Long> ACKNOWLEDGE = script("acknowledge");
    /**
     * 原子合并最新消息并登记待办；同一窗口的新消息不会延后首次落库时间。
     */
    public void record(RoomActivityUpdate update, long windowMillis) {
        Objects.requireNonNull(RedisUtils.execute(RECORD, keys(update.roomId()),
                update.roomId().toString(), update.msgId().toString(),
                Long.toString(update.msgTime()), Long.toString(windowMillis)),
                "房间活跃状态入队失败：Redis 脚本未返回执行结果");
    }

    /**
     * 按到期时间获取有限数量的房间，不在读取时移除待办，以便失败后重试。
     */
    public Set<Long> dueRooms(int limit) {
        // 与入队脚本统一使用 Redis 时间，避免应用实例之间的时钟偏差影响窗口判断。
        Long now = Objects.requireNonNull(RedisUtils.serverTimeMillis(),
                "获取 Redis 服务器时间失败：未返回时间");
        Set<String> rooms = RedisUtils.zRangeByScore(PENDING_KEY, 0, now, 0, limit);
        return Objects.requireNonNull(rooms, "查询到期待写房间失败：Redis 未返回查询结果")
                .stream().map(Long::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 用一次 HMGET 读取消息 ID 和时间，保证两个字段属于同一份快照。
     */
    public RoomActivityUpdate get(Long roomId) {
        List<String> values = RedisUtils.hMultiGet(latestKey(roomId), Arrays.asList("msgId", "msgTime"));
        if (values.size() != 2 || values.get(0) == null || values.get(1) == null) {
            throw new IllegalStateException("房间待写活跃状态缺失或不完整，房间 ID：" + roomId);
        }
        return new RoomActivityUpdate(roomId, Long.valueOf(values.get(0)), Long.parseLong(values.get(1)));
    }

    /**
     * 仅确认已成功落库的快照；若期间产生了更新的消息，则保留并进入下一窗口。
     */
    public void acknowledge(RoomActivityUpdate update, long windowMillis) {
        Objects.requireNonNull(RedisUtils.execute(ACKNOWLEDGE, keys(update.roomId()),
                update.roomId().toString(), update.msgId().toString(), Long.toString(windowMillis)),
                "确认房间活跃状态落库失败：Redis 脚本未返回执行结果");
    }

    private static List<String> keys(Long roomId) {
        // 顺序对应 Lua 的 KEYS[1]（房间快照）和 KEYS[2]（待写队列），两者使用相同哈希标签。
        return Arrays.asList(latestKey(roomId), PENDING_KEY);
    }

    private static String latestKey(Long roomId) {
        return RedisKey.getKey(RedisKey.ROOM_ACTIVITY_LATEST, roomId);
    }

    private static DefaultRedisScript<Long> script(String name) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/room-activity-" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }
}
