package com.openchat.common.event;

/** 移出系统消息在成员关系变更的同一事务中落库并登记普通群广播。 */
public record GroupRemovalMessageEvent(Long messageId) {
}
