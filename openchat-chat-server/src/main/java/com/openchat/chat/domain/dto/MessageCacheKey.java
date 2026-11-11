package com.openchat.chat.domain.dto;

import com.openchat.common.exception.BusinessException;

/** 消息表由 DAO 限定，房间与消息 ID 共同限定缓存记录归属。 */
public record MessageCacheKey(Long roomId, Long msgId) {
    public MessageCacheKey {
        if (roomId == null || roomId <= 0 || msgId == null || msgId <= 0) {
            throw new BusinessException("无效的消息缓存范围");
        }
    }
}
