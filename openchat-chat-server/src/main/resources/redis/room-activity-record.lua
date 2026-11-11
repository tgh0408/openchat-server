-- KEYS：房间快照 Hash、待写房间 ZSet。
-- ARGV：房间 ID、消息 ID、消息创建时间（毫秒）、合并窗口（毫秒）。
-- 复制脚本产生的写命令，兼容使用 TIME 的旧版 Redis 脚本复制行为。
redis.replicate_commands()
local current = redis.call('HGET', KEYS[1], 'msgId')
local incoming = ARGV[2]
-- 按长度和字典序比较正整数 ID，避免 Lua 浮点数转换丢失 Java long 的精度。
if not current or #incoming > #current or (#incoming == #current and incoming > current) then
    redis.call('HSET', KEYS[1], 'msgId', incoming, 'msgTime', ARGV[3])
end
local now = redis.call('TIME')
local due = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[4])
-- 只在房间首次进入待办时设置期限，持续收到消息也不会不断推迟落库。
redis.call('ZADD', KEYS[2], 'NX', due, ARGV[1])
return 1
