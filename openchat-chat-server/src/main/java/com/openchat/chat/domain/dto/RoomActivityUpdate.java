package com.openchat.chat.domain.dto;

/**
 * 房间待写活跃状态的不可变快照，消息 ID 与创建时间始终配对保存。
 *
 * @param msgTime 消息创建时间的毫秒时间戳，不是入队或落库时间。
 */
public record RoomActivityUpdate(Long roomId, Long msgId, long msgTime) {
}
