package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.openchat.rtc.domain.*;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.webrtc.domain.entity.*;
import com.openchat.webrtc.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/** 群成员的唯一写入入口；成员状态、当前参与记录和整通状态同事务提交。 */
@Repository
@RequiredArgsConstructor
public class RtcGroupCallDao {
    private final RtcGroupCallMemberHistoryMapper history;
    private final RtcGroupCallMemberMapper members;
    private final RtcCallMapper calls;
    private final RtcMediaDao media;

    public List<RtcGroupCallMemberHistory> list(String callId) {
        return history.selectList(new LambdaQueryWrapper<RtcGroupCallMemberHistory>()
                .eq(RtcGroupCallMemberHistory::getCallId, callId)
                .orderByAsc(RtcGroupCallMemberHistory::getCreatedAt, RtcGroupCallMemberHistory::getUid));
    }

    /** 主表已经插入；已知的邀请、设备和时间一次写齐，失败由外层创建事务回滚。 */
    public void insert(RtcCallRecord call) {
        for (Long uid : call.participantUids()) {
            RtcParticipantState state = call.participantStates().get(uid);
            RtcDevice device = call.participantDevices().get(uid);
            var row = newHistory(call, uid, state, device, call.createdAt());
            row.setInvitedAt(Date.from(call.createdAt()));
            requireOne(history.insert(row));
            if (state == RtcParticipantState.ACCEPTED) insertCurrent(row);
        }
    }

    /** 整通终态已条件更新成功；同事务关闭成员及当前参与行，不能遗留占用。 */
    public void closeFromWebhook(RtcCallRecord call, Instant endedAt) {
        int accepted = 0;
        for (var member : list(call.callId())) {
            RtcParticipantState previous = member.getState();
            switch (previous) {
                case ACCEPTED -> {
                    accepted++;
                    member.setState(RtcParticipantState.LEFT);
                    member.setLeftAt(Date.from(endedAt));
                }
                case INVITED -> member.setState(RtcParticipantState.EXPIRED);
                default -> {
                    continue;
                }
            }
            member.setUpdatedAt(new Date());
            requireOne(history.update(member, new LambdaUpdateWrapper<RtcGroupCallMemberHistory>()
                    .eq(RtcGroupCallMemberHistory::getCallId, call.callId())
                    .eq(RtcGroupCallMemberHistory::getUid, member.getUid())
                    .eq(RtcGroupCallMemberHistory::getState, previous)));
        }
        int removed = members.delete(new LambdaQueryWrapper<RtcGroupCallMember>()
                .eq(RtcGroupCallMember::getRoomId, call.roomId())
                .eq(RtcGroupCallMember::getCallId, call.callId()));
        if (removed != accepted) throw new RtcCallConflictException();
    }

    /**
     * 不同成员不能比较主表旧版本，否则又退化成整通乐观锁竞争。
     * 先以活动状态推进主表通知序号，再变更当前成员，协调同一通话的跨节点写入。
     * READ_COMMITTED 保证退出后统计当前成员时能看见前一事务的提交；成员仍交给 @Version 插件裁决。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public boolean update(
            RtcCallRecord call, Long uid, RtcParticipantState next,
            RtcDevice device, long expectedMemberVersion, Instant now) {
        var previous = history.selectOne(new LambdaQueryWrapper<RtcGroupCallMemberHistory>()
                .eq(RtcGroupCallMemberHistory::getCallId, call.callId())
                .eq(RtcGroupCallMemberHistory::getUid, uid));
        if (previous != null && previous.getVersion() != expectedMemberVersion) return false;
        if (previous == null && (next != RtcParticipantState.ACCEPTED || expectedMemberVersion != 1)) return false;
        boolean joining = next == RtcParticipantState.ACCEPTED;
        boolean leaving = previous != null && previous.getState() == RtcParticipantState.ACCEPTED;
        if (joining && leaving) return false;
        var aggregate = new LambdaUpdateWrapper<RtcCall>()
                .eq(RtcCall::getCallId, call.callId())
                .isNull(RtcCall::getCalleeUid)
                .in(RtcCall::getState, RtcCallState.RINGING, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                .setSql("version = version + 1")
                .set(RtcCall::getUpdatedAt, Date.from(now));
        if (joining) {
            aggregate.setSql("accepted_at = COALESCE(accepted_at, {0})", Date.from(now))
                    .setSql("state = CASE WHEN state = 'RINGING' THEN 'ACCEPTED' ELSE state END");
        }
        if (next == RtcParticipantState.EXPIRED) aggregate.apply("expires_at <= CURRENT_TIMESTAMP(3)");
        if (calls.update(null, aggregate) != 1) return false;

        if (previous == null) {
            previous = newHistory(call, uid, next, device, now);
            requireOne(history.insert(previous));
        } else {
            RtcParticipantState oldState = previous.getState();
            var condition = new LambdaUpdateWrapper<RtcGroupCallMemberHistory>()
                    .eq(RtcGroupCallMemberHistory::getCallId, call.callId())
                    .eq(RtcGroupCallMemberHistory::getUid, uid)
                    .eq(RtcGroupCallMemberHistory::getState, oldState);
            previous.setState(next);
            previous.setUpdatedAt(Date.from(now));
            if (joining) {
                previous.setTerminal(device.terminal());
                previous.setDeviceId(device.deviceId());
                previous.setAcceptedAt(Date.from(now));
                previous.setLeftAt(null);
                condition.set(RtcGroupCallMemberHistory::getLeftAt, null);
            } else if (leaving) {
                previous.setLeftAt(Date.from(now));
            }
            requireOne(history.update(previous, condition));
        }
        if (joining) {
            insertCurrent(previous);
        } else if (leaving) {
            requireOne(members.delete(new LambdaQueryWrapper<RtcGroupCallMember>()
                    .eq(RtcGroupCallMember::getRoomId, call.roomId())
                    .eq(RtcGroupCallMember::getUid, uid)
                    .eq(RtcGroupCallMember::getCallId, call.callId())
                    .eq(RtcGroupCallMember::getTerminal, previous.getTerminal())
                    .eq(RtcGroupCallMember::getDeviceId, previous.getDeviceId())));
        }
        if (leaving) finishIfEmpty(call.roomId(), call.callId(), uid, now);
        return true;
    }

    /** 各成员先完成主表更新才变更参与行；同事务删除后统计，避免并发退出漏结束或结束新加入者。 */
    private void finishIfEmpty(Long roomId, String callId, Long uid, Instant now) {
        long remaining = members.selectCount(new LambdaQueryWrapper<RtcGroupCallMember>()
                .eq(RtcGroupCallMember::getRoomId, roomId)
                .eq(RtcGroupCallMember::getCallId, callId));
        if (remaining != 0) return;
        RtcCall call = calls.selectById(callId);
        RtcCallState terminal = call.getAcceptedAt() == null ? RtcCallState.CANCELED : RtcCallState.ENDED;
        Date started = call.getConnectedAt() == null ? call.getAcceptedAt() : call.getConnectedAt();
        var condition = new LambdaUpdateWrapper<RtcCall>()
                .eq(RtcCall::getCallId, callId)
                .eq(RtcCall::getState, call.getState())
                .set(RtcCall::getActiveKey, null);
        call.setState(terminal);
        call.setEndedAt(Date.from(now));
        call.setEndedByUid(uid);
        call.setEndReason(RtcEndReason.fromState(terminal).getValue());
        call.setDurationSeconds(started == null ? 0L : Math.max(0L,
                Duration.between(started.toInstant(), now).getSeconds()));
        requireOne(calls.update(call, condition));
        media.deleteByCallId(callId);
    }

    private RtcGroupCallMemberHistory newHistory(
            RtcCallRecord call, Long uid, RtcParticipantState state, RtcDevice device, Instant now) {
        var row = new RtcGroupCallMemberHistory();
        row.setCallId(call.callId());
        row.setUid(uid);
        row.setRoomId(call.roomId());
        row.setState(state);
        row.setVersion(1);
        row.setCreatedAt(Date.from(now));
        row.setUpdatedAt(Date.from(now));
        if (device != null) {
            row.setTerminal(device.terminal());
            row.setDeviceId(device.deviceId());
        }
        if (state == RtcParticipantState.ACCEPTED) row.setAcceptedAt(Date.from(now));
        return row;
    }

    private void insertCurrent(RtcGroupCallMemberHistory row) {
        var member = new RtcGroupCallMember();
        member.setRoomId(row.getRoomId());
        member.setUid(row.getUid());
        member.setCallId(row.getCallId());
        member.setTerminal(row.getTerminal());
        member.setDeviceId(row.getDeviceId());
        member.setJoinedAt(row.getAcceptedAt());
        requireOne(members.insert(member));
    }

    private void requireOne(int affected) {
        if (affected != 1) throw new RtcCallConflictException();
    }
}
