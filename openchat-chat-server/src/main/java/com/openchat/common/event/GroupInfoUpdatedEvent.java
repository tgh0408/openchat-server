package com.openchat.common.event;

/** 群资料修改提示已落库，在原业务事务中登记可靠投递。 */
public record GroupInfoUpdatedEvent(Long messageId) {
}
