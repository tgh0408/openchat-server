package com.openchat.rtc.domain;

import java.time.Instant;
import java.util.*;

/**
 * 呼叫持久化模型。
 */
public record RtcCallRecord(
        String callId,
        Long roomId,
        Long callerUid,
        Long calleeUid,
        RtcMediaMode mediaMode,
        RtcCallState state,
        Integer version,
        String clientRequestId,
        String activeKey,
        Instant expiresAt,
        String endReason,
        Instant createdAt,
        Instant updatedAt,
        Instant endedAt,
        Instant acceptedAt,
        Instant connectedAt,
        Long durationSeconds,
        Long endedByUid,
        Long summaryMessageId,
        List<Long> participantUids,
        Map<Long, RtcParticipantState> participantStates,
        Map<Long, RtcDevice> participantDevices,
        Map<Long, Integer> participantVersions) {

    /** 单聊及新建群通话的构造入口；群成员版本由持久化读取补全。 */
    public RtcCallRecord(
            String callId, Long roomId, Long callerUid, Long calleeUid,
            RtcMediaMode mediaMode, RtcCallState state, Integer version,
            String clientRequestId, String activeKey, Instant expiresAt,
            String endReason, Instant createdAt, Instant updatedAt, Instant endedAt,
            Instant acceptedAt, Instant connectedAt, Long durationSeconds,
            Long endedByUid, Long summaryMessageId, List<Long> participantUids,
            Map<Long, RtcParticipantState> participantStates, Map<Long, RtcDevice> participantDevices) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, clientRequestId,
                activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt, acceptedAt, connectedAt,
                durationSeconds, endedByUid, summaryMessageId, participantUids, participantStates,
                participantDevices, Map.of());
    }

    public RtcCallRecord {
        requireText(callId, "callId");
        requirePositive(roomId, "roomId");
        requirePositive(callerUid, "callerUid");
        if (calleeUid != null) requirePositive(calleeUid, "calleeUid");
        if (callerUid.equals(calleeUid)) {
            throw new IllegalArgumentException("呼叫双方不能相同");
        }
        if (mediaMode == null || state == null || version < 1) {
            throw new IllegalArgumentException("呼叫状态参数无效");
        }
        requireText(clientRequestId, "clientRequestId");
        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds 无效");
        }
        if (endedByUid != null && endedByUid <= 0) {
            throw new IllegalArgumentException("endedByUid 无效");
        }
        if (summaryMessageId != null && summaryMessageId <= 0) {
            throw new IllegalArgumentException("summaryMessageId 无效");
        }
        if (participantUids == null || participantUids.isEmpty()
                || !participantUids.contains(callerUid)
                || (calleeUid != null && !participantUids.contains(calleeUid))
                || participantUids.stream().anyMatch(uid -> uid == null || uid <= 0)
                || participantUids.stream().distinct().count() != participantUids.size()) {
            throw new IllegalArgumentException("RTC 参与者无效");
        }
        participantUids = List.copyOf(participantUids);
        if (participantStates == null) {
            Map<Long, RtcParticipantState> initial = new LinkedHashMap<>();
            for (Long uid : participantUids) {
                RtcParticipantState initialState = RtcParticipantState.INVITED;
                if (uid.equals(callerUid)) {
                    initialState = RtcParticipantState.ACCEPTED;
                } else if (state == RtcCallState.EXPIRED) {
                    initialState = RtcParticipantState.EXPIRED;
                } else if (state == RtcCallState.REJECTED) {
                    initialState = RtcParticipantState.REJECTED;
                } else if (participantUids.size() == 2
                        && (state == RtcCallState.ACCEPTED || state == RtcCallState.CONNECTED
                        || state == RtcCallState.ENDED)) {
                    initialState = RtcParticipantState.ACCEPTED;
                }
                initial.put(uid, initialState);
            }
            participantStates = initial;
        }
        if (!participantStates.keySet().equals(new java.util.HashSet<>(participantUids))
                || participantStates.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("RTC 参与者响应状态无效");
        }
        participantStates = Map.copyOf(participantStates);
        participantDevices = participantDevices == null ? Map.of() : Map.copyOf(participantDevices);
        participantVersions = Map.copyOf(participantVersions);
        if (!new HashSet<>(participantUids).containsAll(participantDevices.keySet())) {
            throw new IllegalArgumentException("RTC 设备归属包含非参与者");
        }
    }

    /** 旧持久化记录没有可验证的设备归属，不推测为当前设备。 */
    public RtcCallRecord(
            String callId, Long roomId, Long callerUid, Long calleeUid,
            RtcMediaMode mediaMode, RtcCallState state, Integer version,
            String clientRequestId, String activeKey, Instant expiresAt,
            String endReason, Instant createdAt, Instant updatedAt, Instant endedAt,
            Instant acceptedAt, Instant connectedAt, Long durationSeconds,
            Long endedByUid, Long summaryMessageId, List<Long> participantUids,
            Map<Long, RtcParticipantState> participantStates) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, clientRequestId,
                activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                acceptedAt, connectedAt, durationSeconds, endedByUid, summaryMessageId,
                participantUids, participantStates, Map.of());
    }

    /** 将本次接听设备加入待写入快照，保留原版本供同一次条件更新使用。 */
    public RtcCallRecord withDevice(Long uid, RtcDevice device) {
        Map<Long, RtcDevice> devices = new LinkedHashMap<>(participantDevices);
        if (device == null || devices.putIfAbsent(uid, device) != null) {
            throw new IllegalStateException("通话设备不能重复分配");
        }
        return new RtcCallRecord(callId, roomId, callerUid, calleeUid, mediaMode, state, version,
                clientRequestId, activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                acceptedAt, connectedAt, durationSeconds, endedByUid, summaryMessageId,
                participantUids, participantStates, devices, participantVersions);
    }

    /** 群成员主动加入或离开后重入，成员、状态与设备保留同一版本待原子提交。 */
    public RtcCallRecord withAcceptedParticipant(Long uid, RtcDevice device) {
        requirePositive(uid, "uid");
        if (device == null || participantStates.get(uid) == RtcParticipantState.ACCEPTED) {
            throw new IllegalStateException("不能覆盖正在参与通话的设备");
        }
        List<Long> uids = new ArrayList<>(participantUids);
        if (!uids.contains(uid)) uids.add(uid);
        Map<Long, RtcParticipantState> states = new LinkedHashMap<>(participantStates);
        states.put(uid, RtcParticipantState.ACCEPTED);
        Map<Long, RtcDevice> devices = new LinkedHashMap<>(participantDevices);
        devices.put(uid, device);
        return new RtcCallRecord(callId, roomId, callerUid, calleeUid, mediaMode, state, version,
                clientRequestId, activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                acceptedAt, connectedAt, durationSeconds, endedByUid, summaryMessageId,
                uids, states, devices, participantVersions);
    }

    /** 保留已有构造入口，新呼叫初始化邀请状态。 */
    public RtcCallRecord(
            String callId, Long roomId, Long callerUid, Long calleeUid,
            RtcMediaMode mediaMode, RtcCallState state, Integer version,
            String clientRequestId, String activeKey, Instant expiresAt,
            String endReason, Instant createdAt, Instant updatedAt, Instant endedAt,
            Instant acceptedAt, Instant connectedAt, Long durationSeconds,
            Long endedByUid, Long summaryMessageId, List<Long> participantUids) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, clientRequestId,
                activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                acceptedAt, connectedAt, durationSeconds, endedByUid, summaryMessageId,
                participantUids, null);
    }

    /**
     * 兼容现有调用方；新增时间和消息幂等字段默认为空。
     */
    public RtcCallRecord(
            String callId,
            Long roomId,
            Long callerUid,
            Long calleeUid,
            RtcMediaMode mediaMode,
            RtcCallState state,
            Integer version,
            String clientRequestId,
            String activeKey,
            Instant expiresAt,
            String endReason,
            Instant createdAt,
            Instant updatedAt,
            Instant endedAt) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, clientRequestId,
                activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                null, null, null, null, null, List.of(callerUid, calleeUid));
    }

    /**
     * 创建带参与者快照的呼叫记录。
     */
    public RtcCallRecord(
            String callId,
            Long roomId,
            Long callerUid,
            Long calleeUid,
            RtcMediaMode mediaMode,
            RtcCallState state,
            Integer version,
            String clientRequestId,
            String activeKey,
            Instant expiresAt,
            String endReason,
            Instant createdAt,
            Instant updatedAt,
            Instant endedAt,
            List<Long> participantUids) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, clientRequestId,
                activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                null, null, null, null, null, participantUids);
    }

    /**
     * 兼容未携带参与者快照的持久化读取调用方。
     */
    public RtcCallRecord(
            String callId,
            Long roomId,
            Long callerUid,
            Long calleeUid,
            RtcMediaMode mediaMode,
            RtcCallState state,
            Integer version,
            String clientRequestId,
            String activeKey,
            Instant expiresAt,
            String endReason,
            Instant createdAt,
            Instant updatedAt,
            Instant endedAt,
            Instant acceptedAt,
            Instant connectedAt,
            Long durationSeconds,
            Long endedByUid,
            Long summaryMessageId) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, clientRequestId,
                activeKey, expiresAt, endReason, createdAt, updatedAt, endedAt,
                acceptedAt, connectedAt, durationSeconds, endedByUid, summaryMessageId,
                List.of(callerUid, calleeUid));
    }

    /**
     * 群通话没有唯一被叫，不按参与人数推断房间类型。
     */
    public boolean isGroup() {
        return calleeUid == null;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " 无效");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " 无效");
        }
    }
}
