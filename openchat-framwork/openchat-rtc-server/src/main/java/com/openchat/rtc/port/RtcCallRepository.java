package com.openchat.rtc.port;

import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcParticipantState;

import java.time.Instant;
import java.util.Optional;
import java.util.Map;
import java.util.List;

/**
 * RTC 数据持久化端口。
 */
public interface RtcCallRepository {

    Optional<RtcCallRecord> findByCallId(String callId);

    Optional<RtcCallRecord> findByCallerAndClientRequestId(Long callerUid, String clientRequestId);

    Optional<RtcCallRecord> findByActiveKey(String activeKey);

    boolean insert(RtcCallRecord call);

    /** 群成员独立条件更新；维护当前成员及通知版本，退出后按当前成员数量判定终态，调用方须有事务。 */
    boolean updateGroupParticipant(
            RtcCallRecord previous, Long uid, RtcParticipantState state,
            com.openchat.rtc.domain.RtcDevice device, long expectedMemberVersion, Instant now);

    /**
     * 单聊同一行内原子提交设备及整通状态；双方状态由整通状态派生，调用方必须处于事务。
     * accepting 表示需校验单聊邀请截止；群聊必须走 updateGroupParticipant。
     */
    boolean updateParticipants(
            RtcCallRecord previous,
            Map<Long, RtcParticipantState> states,
            RtcCallState nextState,
            String endReason,
            Long actorUid,
            Instant now,
            boolean accepting,
            boolean expiring);

    /** 有界查询已到期且仍有未响应成员的呼叫，不在查询阶段宣称领取成功。 */
    List<String> findDueInvitations(int limit);

    boolean transition(
            String callId,
            long expectedVersion,
            RtcCallState expectedState,
            RtcCallState nextState,
            String endReason,
            Long endedByUid,
            Instant now);

    /**
     * 终态摘要消息的唯一认领。只有第一个事务可以写入消息 ID。
     */
    boolean claimSummaryMessage(String callId, Long messageId);
}
