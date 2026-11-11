package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.openchat.common.utils.JsonUtils;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcMediaMode;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.RtcDevice;
import com.openchat.rtc.domain.RtcEndReason;
import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.webrtc.domain.entity.RtcCall;
import com.openchat.webrtc.mapper.RtcCallMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** RTC 呼叫持久化入口；通过 MyBatis-Plus 乐观锁协调多节点状态更新。 */
@Repository
@RequiredArgsConstructor
public class RtcCallDao implements RtcCallRepository {
    private final RtcCallMapper mapper;
    private final RtcMediaDao mediaDao;
    private final RtcGroupCallDao groupDao;

    /** 单聊按双方定位，群聊按成员历史定位；过滤已经结束的整通记录。 */
    public List<RtcCallRecord> findActiveByParticipant(Long uid) {
        return mapper.selectList(new LambdaQueryWrapper<RtcCall>()
                        .in(RtcCall::getState, RtcCallState.RINGING, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                        .and(query -> query.eq(RtcCall::getCallerUid, uid)
                                .or().eq(RtcCall::getCalleeUid, uid)
                                .or().exists("SELECT 1 FROM rtc_group_call_member_history h"
                                        + " WHERE h.call_id = rtc_call.call_id AND h.uid = {0}"
                                        + " AND h.state IN ({1}, {2})", uid,
                                        RtcParticipantState.INVITED.getValue(), RtcParticipantState.ACCEPTED.getValue())))
                .stream().map(this::toRecord)
                .filter(call -> {
                    RtcParticipantState state = call.participantStates().get(uid);
                    return state == RtcParticipantState.INVITED || state == RtcParticipantState.ACCEPTED;
                }).toList();
    }

    /** 只定位当前账号已接听的候选呼叫，房间类型由缓存入口进一步确认。 */
    public List<RtcCall> findOngoingCandidates(Long uid) {
        return mapper.selectList(new LambdaQueryWrapper<RtcCall>()
                        .select(RtcCall::getCallId, RtcCall::getRoomId)
                        .in(RtcCall::getState, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                        .and(query -> query.eq(RtcCall::getCallerUid, uid)
                                .or().eq(RtcCall::getCalleeUid, uid))
                        .orderByDesc(RtcCall::getAcceptedAt)
                        .orderByDesc(RtcCall::getCallId));
    }

    /** 在恢复事务内推进版本，阻止并发挂断清理媒体后又被本次恢复重建。 */
    public boolean claimReconnect(String callId, int version, RtcCallState state) {
        return mapper.update(versionedEntity(callId, version),
                new LambdaUpdateWrapper<RtcCall>()
                        .eq(RtcCall::getCallId, callId)
                        .eq(RtcCall::getState, state)
                        .in(RtcCall::getState, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                        .set(RtcCall::getUpdatedAt, new Date())) == 1;
    }

    /**
     * 不同 eventId 也可能同时到达：先用框架版本更新通话，再读取本事务的事件集合，
     * 防止双方 joined 各自只看到自己而永远漏掉接通。冲突由外层事务回滚。
     */
    public boolean claimWebhook(RtcCallRecord call) {
        return mapper.update(versionedEntity(call.callId(), call.version()),
                new LambdaUpdateWrapper<RtcCall>()
                        .eq(RtcCall::getCallId, call.callId())
                        .eq(RtcCall::getState, call.state())
                        .in(RtcCall::getState, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                        .set(RtcCall::getUpdatedAt, new Date())) == 1;
    }

    /** 媒体连接及终态一次写齐；版本交给插件，终态保留现有媒体清理职责。 */
    public boolean updateFromWebhook(RtcCallRecord call, Instant connectedAt, Instant endedAt) {
        boolean terminal = endedAt != null;
        var update = new LambdaUpdateWrapper<RtcCall>()
                .eq(RtcCall::getCallId, call.callId())
                .eq(RtcCall::getState, call.state())
                .in(RtcCall::getState, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                .set(RtcCall::getUpdatedAt, new Date())
                .set(RtcCall::getConnectedAt, toDate(connectedAt))
                .set(RtcCall::getState, terminal ? RtcCallState.ENDED : RtcCallState.CONNECTED);
        if (terminal) {
            Instant base = connectedAt == null ? call.acceptedAt() : connectedAt;
            update.set(RtcCall::getEndedAt, toDate(endedAt))
                    .set(RtcCall::getEndedByUid, null)
                    .set(RtcCall::getEndReason, RtcEndReason.RTC_CALL_ENDED.getValue())
                    .set(RtcCall::getActiveKey, null)
                    .set(RtcCall::getDurationSeconds, base == null ? 0L
                            : Math.max(0L, Duration.between(base, endedAt).getSeconds()));
        }
        if (mapper.update(versionedEntity(call.callId(), call.version()), update) != 1) return false;
        if (terminal) {
            if (call.isGroup()) groupDao.closeFromWebhook(call, endedAt);
            mediaDao.deleteByCallId(call.callId());
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RtcCallRecord> findByCallId(String callId) {
        return Optional.ofNullable(mapper.selectById(callId))
                .map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RtcCallRecord> findByCallerAndClientRequestId(Long callerUid, String clientRequestId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<RtcCall>()
                        .eq(RtcCall::getCallerUid, callerUid)
                        .eq(RtcCall::getClientRequestId, clientRequestId)
                        .last("LIMIT 1")))
                .map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RtcCallRecord> findByActiveKey(String activeKey) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<RtcCall>()
                        .eq(RtcCall::getActiveKey, activeKey)
                        .last("LIMIT 1")))
                .map(this::toRecord);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public boolean insert(RtcCallRecord call) {
        RtcCall entity = new RtcCall();
        entity.setCallId(call.callId());
        entity.setRoomId(call.roomId());
        entity.setCallerUid(call.callerUid());
        entity.setCalleeUid(call.calleeUid());
        entity.setMediaMode(call.mediaMode().name());
        entity.setState(call.state());
        entity.setVersion(call.version());
        entity.setClientRequestId(call.clientRequestId());
        entity.setActiveKey(call.activeKey());
        entity.setExpiresAt(toDate(call.expiresAt()));
        entity.setEndReason(call.endReason());
        entity.setCreatedAt(toDate(call.createdAt()));
        entity.setUpdatedAt(toDate(call.updatedAt()));
        entity.setEndedAt(toDate(call.endedAt()));
        entity.setAcceptedAt(toDate(call.acceptedAt()));
        entity.setConnectedAt(toDate(call.connectedAt()));
        entity.setDurationSeconds(call.durationSeconds());
        entity.setEndedByUid(call.endedByUid());
        entity.setSummaryMessageId(call.summaryMessageId());
        entity.setParticipantDevices(call.isGroup() ? null : JsonUtils.toStr(call.participantDevices()));
        try {
            if (mapper.insert(entity) != 1) return false;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
        if (call.isGroup()) groupDao.insert(call);
        return true;
    }

    @Override
    public boolean updateGroupParticipant(RtcCallRecord previous, Long uid, RtcParticipantState state,
            RtcDevice device, long expectedMemberVersion, Instant now) {
        return groupDao.update(previous, uid, state, device, expectedMemberVersion, now);
    }

    @Override
    public List<String> findDueInvitations(int limit) {
        // 群邀请只读成员表，单聊只扫描响铃；不再访问已删除的 JSON 列。
        return mapper.selectList(new LambdaQueryWrapper<RtcCall>()
                        .select(RtcCall::getCallId)
                        .in(RtcCall::getState, RtcCallState.RINGING, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                        .le(RtcCall::getExpiresAt, Date.from(Instant.now().minusSeconds(5)))
                        .and(query -> query
                                .and(single -> single.isNotNull(RtcCall::getCalleeUid)
                                        .eq(RtcCall::getState, RtcCallState.RINGING))
                                .or().exists("SELECT 1 FROM rtc_group_call_member_history h"
                                        + " WHERE h.call_id = rtc_call.call_id AND h.state = {0}",
                                        RtcParticipantState.INVITED.getValue()))
                        .orderByAsc(RtcCall::getExpiresAt, RtcCall::getCallId)
                        .last("LIMIT " + Math.clamp(limit, 1, 200)))
                .stream().map(RtcCall::getCallId).toList();
    }

    @Override
    public boolean updateParticipants(
            RtcCallRecord previous, Map<Long, RtcParticipantState> states,
            RtcCallState nextState, String endReason, Long actorUid, Instant now,
            boolean accepting, boolean expiring) {
        if (previous.isGroup()) throw new IllegalArgumentException("群通话必须独立更新成员");
        Date updatedAt = toDate(now);
        boolean terminal = nextState.terminal();
        LambdaUpdateWrapper<RtcCall> update = new LambdaUpdateWrapper<RtcCall>()
                .eq(RtcCall::getCallId, previous.callId())
                .eq(RtcCall::getState, previous.state())
                .in(RtcCall::getState, RtcCallState.RINGING, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)
                .set(RtcCall::getParticipantDevices, JsonUtils.toStr(previous.participantDevices()))
                .set(RtcCall::getState, nextState)
                .set(RtcCall::getUpdatedAt, updatedAt);
        if (accepting) {
            update.apply("expires_at > CURRENT_TIMESTAMP(3)");
        }
        if (expiring) {
            update.apply("expires_at <= CURRENT_TIMESTAMP(3)");
        }
        if (nextState == RtcCallState.ACCEPTED) {
            update.set(RtcCall::getAcceptedAt,
                    previous.acceptedAt() == null ? updatedAt : toDate(previous.acceptedAt()));
        }
        if (terminal) {
            update.set(RtcCall::getEndReason, endReason)
                    .set(RtcCall::getEndedByUid, actorUid)
                    .set(RtcCall::getActiveKey, null)
                    .set(RtcCall::getEndedAt, updatedAt)
                    .set(RtcCall::getDurationSeconds, durationSeconds(previous, now));
        }

        int updated = mapper.update(versionedEntity(previous.callId(), previous.version()), update);
        if (updated > 0 && terminal) {
            mediaDao.deleteByCallId(previous.callId());
        }
        return updated > 0;
    }

    @Override
    public boolean transition(
            String callId,
            long expectedVersion,
            RtcCallState expectedState,
            RtcCallState nextState,
            String endReason,
            Long endedByUid,
            Instant now) {
        RtcCall current = mapper.selectById(callId);
        if (current == null) {
            return false;
        }

        Date updatedAt = toDate(now);
        boolean terminal = nextState.terminal();
        LambdaUpdateWrapper<RtcCall> update = new LambdaUpdateWrapper<RtcCall>()
                .eq(RtcCall::getCallId, callId)
                .eq(RtcCall::getState, expectedState)
                .set(RtcCall::getState, nextState)
                .set(RtcCall::getUpdatedAt, updatedAt)
                .set(RtcCall::getEndReason, endReason);
        if (nextState == RtcCallState.ACCEPTED) {
            update.set(RtcCall::getAcceptedAt, updatedAt);
        }
        if (terminal) {
            update.set(RtcCall::getEndedByUid, endedByUid)
                    .set(RtcCall::getActiveKey, null)
                    .set(RtcCall::getEndedAt, updatedAt)
                    .set(RtcCall::getDurationSeconds, durationSeconds(current, now));
        }

        int updated = mapper.update(versionedEntity(callId, expectedVersion), update);
        if (updated > 0 && terminal) {
            mediaDao.deleteByCallId(callId);
        }
        return updated > 0;
    }

    @Override
    public boolean claimSummaryMessage(String callId, Long messageId) {
        RtcCall current = mapper.selectById(callId);
        if (current == null || !current.getState().terminal() || current.getSummaryMessageId() != null) {
            return false;
        }

        RtcCall entity = versionedEntity(callId, current.getVersion());
        LambdaUpdateWrapper<RtcCall> update = new LambdaUpdateWrapper<RtcCall>()
                .eq(RtcCall::getCallId, callId)
                .eq(RtcCall::getState, current.getState())
                .isNull(RtcCall::getSummaryMessageId)
                .set(RtcCall::getSummaryMessageId, messageId);
        return mapper.update(entity, update) > 0;
    }

    private RtcCall versionedEntity(String callId, long version) {
        RtcCall entity = new RtcCall();
        entity.setCallId(callId);
        entity.setVersion(Math.toIntExact(version));
        return entity;
    }

    private long durationSeconds(RtcCallRecord call, Instant now) {
        Instant base = call.connectedAt() != null ? call.connectedAt() : call.acceptedAt();
        return base == null ? 0L : Math.max(0L, Duration.between(base, now).getSeconds());
    }

    private long durationSeconds(RtcCall call, Instant now) {
        Date base = call.getConnectedAt() != null ? call.getConnectedAt() : call.getAcceptedAt();
        return base == null
                ? 0L
                : Math.max(0L, Duration.between(base.toInstant(), now).getSeconds());
    }

    private RtcCallRecord toRecord(RtcCall entity) {
        List<Long> uids;
        Map<Long, RtcParticipantState> states = null;
        Map<Long, RtcDevice> devices = new LinkedHashMap<>();
        Map<Long, Integer> versions = new LinkedHashMap<>();
        if (entity.getCalleeUid() == null) {
            var members = groupDao.list(entity.getCallId());
            uids = members.stream().map(com.openchat.webrtc.domain.entity.RtcGroupCallMemberHistory::getUid).toList();
            states = new LinkedHashMap<>();
            for (var member : members) {
                states.put(member.getUid(), member.getState());
                versions.put(member.getUid(), member.getVersion());
                if (member.getDeviceId() != null) {
                    devices.put(member.getUid(), new RtcDevice(member.getTerminal(), member.getDeviceId()));
                }
            }
        } else {
            uids = List.of(entity.getCallerUid(), entity.getCalleeUid());
            if (entity.getParticipantDevices() != null) {
                devices = JsonUtils.toObj(entity.getParticipantDevices(), new TypeReference<Map<Long, RtcDevice>>() {});
            }
        }
        return new RtcCallRecord(
                entity.getCallId(),
                entity.getRoomId(),
                entity.getCallerUid(),
                entity.getCalleeUid(),
                RtcMediaMode.valueOf(entity.getMediaMode()),
                entity.getState(),
                entity.getVersion(),
                entity.getClientRequestId(),
                entity.getActiveKey(),
                toInstant(entity.getExpiresAt()),
                entity.getEndReason(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt()),
                toInstant(entity.getEndedAt()),
                toInstant(entity.getAcceptedAt()),
                toInstant(entity.getConnectedAt()),
                entity.getDurationSeconds(),
                entity.getEndedByUid(),
                entity.getSummaryMessageId(),
                uids, states, devices, versions);
    }

    private Date toDate(Instant value) {
        return value == null ? null : Date.from(value);
    }

    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }
}
