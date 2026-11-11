package com.openchat.common.event;

/** 已在当前事务内保存的入群或拒绝提示；仅传递标识，可靠重试不再执行邀请业务。 */
public record GroupInvitationEvent(Long messageId) {
}
