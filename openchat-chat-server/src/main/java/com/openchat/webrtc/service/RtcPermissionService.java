package com.openchat.webrtc.service;

import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.common.exception.BusinessException;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.vo.CreateCallCommand;
import com.openchat.rtc.port.RtcCallParticipants;
import com.openchat.rtc.port.RtcPermissionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RTC 业务权限适配器。单聊沿用好友权限，群聊使用当前成员快照。
 */
@Service
@RequiredArgsConstructor
public class RtcPermissionService implements RtcPermissionPort {
    private final RoomAccessService roomAccessService;
    private final GroupMemberDao groupMemberDao;

    /** 群通话发现只要求当前有效群成员，不要求已有通话参与记录。 */
    public void requireGroupMember(Long uid, Long roomId) {
        if (roomAccessService.requireCurrentMember(uid, roomId).getType() != RoomTypeEnum.GROUP) {
            throw new BusinessException("当前房间不是群聊");
        }
    }

    @Override
    public RtcCallParticipants requireCreate(Long callerUid, CreateCallCommand command) {
        Room room = roomAccessService.requireCanSend(callerUid, command.roomId());
        if (room.getType() == RoomTypeEnum.GROUP) {
            Set<Long> members = groupMemberDao.getMemberUidSet(command.roomId()).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!members.contains(callerUid) || members.size() < 2) {
                throw new BusinessException("当前群聊没有可用的通话成员");
            }
            List<Long> invitees = command.participantUids() == null
                    ? members.stream()
                    .filter(uid -> !Objects.equals(uid, callerUid))
                    .toList()
                    : command.participantUids();
            if (invitees.isEmpty()
                    || invitees.stream().anyMatch(uid -> Objects.equals(uid, callerUid))
                    || !members.containsAll(invitees)) {
                throw new BusinessException("通话成员选择无效");
            }
            List<Long> participants = new ArrayList<>(invitees.size() + 1);
            participants.add(callerUid);
            participants.addAll(invitees);
            return new RtcCallParticipants(command.roomId(), callerUid, null, participants);
        }
        if (room.getType() != RoomTypeEnum.FRIEND) {
            throw new BusinessException("当前房间不支持 RTC 通话");
        }
        Long calleeUid = roomAccessService.requireFriendPeer(callerUid, command.roomId());
        return new RtcCallParticipants(command.roomId(), callerUid, calleeUid);
    }

    @Override
    public void requireParticipant(RtcCallRecord call, Long uid) {
        if (uid == null || !call.participantUids().contains(uid)) {
            throw new BusinessException("无权访问当前 RTC 通话");
        }
        if (isGroup(call)) {
            roomAccessService.requireCurrentMember(uid, call.roomId());
        }
    }

    @Override
    public void requireJoin(RtcCallRecord call, Long uid) {
        if (isGroup(call)) {
            roomAccessService.requireCurrentMember(uid, call.roomId());
        } else {
            requireParticipant(call, uid);
        }
    }

    @Override
    public boolean isGroup(RtcCallRecord call) {
        return roomAccessService.requireRoom(call.roomId()).getType() == RoomTypeEnum.GROUP;
    }
}
