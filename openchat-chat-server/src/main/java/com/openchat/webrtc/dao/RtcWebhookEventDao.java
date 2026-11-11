package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.common.exception.BusinessException;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import com.openchat.webrtc.mapper.RtcWebhookEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 事件与通话更新加入同一事务，event_id 主键在所有实例间去重。 */
@Repository
@RequiredArgsConstructor
public class RtcWebhookEventDao {
    private final RtcWebhookEventMapper mapper;

    /** 仅在持有通话条件写入后的事务内读取，用 SID 区分重建房间和成员重连。 */
    public List<RtcWebhookEvent> listRoomEvents(String callId, String roomSid) {
        return mapper.selectList(new LambdaQueryWrapper<RtcWebhookEvent>()
                .eq(RtcWebhookEvent::getCallId, callId)
                .eq(RtcWebhookEvent::getRoomSid, roomSid)
                .orderByAsc(RtcWebhookEvent::getOccurredAtSeconds, RtcWebhookEvent::getEventId));
    }

    /** 房间代次不明确时保守忽略结束事件，不用同名旧房间结束新会话。 */
    public boolean hasNewerRoom(RtcWebhookEvent event) {
        var query = new LambdaQueryWrapper<RtcWebhookEvent>()
                .eq(RtcWebhookEvent::getCallId, event.getCallId())
                .ne(RtcWebhookEvent::getRoomSid, event.getRoomSid());
        if (event.getRoomCreatedAtSeconds() != null) {
            query.and(room -> room
                    .ge(RtcWebhookEvent::getRoomCreatedAtSeconds, event.getRoomCreatedAtSeconds())
                    .or().ge(RtcWebhookEvent::getOccurredAtSeconds, event.getOccurredAtSeconds()));
        }
        return mapper.selectCount(query) > 0;
    }

    /** 唯一键重复表示已处理；其他写入失败必须交给回调入口返回可重试的失败状态。 */
    public boolean insertIfAbsent(RtcWebhookEvent event) {
        try {
            if (mapper.insert(event) != 1) {
                throw new BusinessException("RTC 回调事件保存失败");
            }
            return true;
        } catch (DuplicateKeyException exception) {
            // 本表只有 event_id 一个唯一约束，重投不修改首次收到的事件。
            return false;
        }
    }
}
