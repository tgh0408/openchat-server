package com.openchat.common.event;

/** 仅携带用户标识；接收方必须重新读取 Redis 权威会话，不能信任迟到的令牌快照。 */
public record LoginSessionChangedEvent(Long uid) {
}
