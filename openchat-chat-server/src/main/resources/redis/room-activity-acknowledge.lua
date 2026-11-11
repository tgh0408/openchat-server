-- KEYS：房间快照 Hash、待写房间 ZSet。
-- ARGV：房间 ID、已落库的消息 ID、下一合并窗口（毫秒）。
redis.replicate_commands()
local current = redis.call('HGET', KEYS[1], 'msgId')
-- 比较和删除在同一脚本中完成，避免误删数据库写入期间到达的新快照。
if current == ARGV[2] then
    redis.call('DEL', KEYS[1])
    redis.call('ZREM', KEYS[2], ARGV[1])
    return 1
end
-- 数据库写入期间出现了更新的消息，保留快照并安排下一窗口。
if current then
    local now = redis.call('TIME')
    local due = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[3])
    -- 仅调整仍存在的待办，不重新创建已经被移除的任务。
    redis.call('ZADD', KEYS[2], 'XX', due, ARGV[1])
end
return 0
