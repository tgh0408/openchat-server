package com.openchat.chat.domain.event;

/** 已成功执行的成员表变更，缓存监听器仅在事务提交后处理。 */
public record GroupMembersChanged(Long roomId, Long uid) {}
