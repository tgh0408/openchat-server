package com.openchat.webrtc.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.annotation.RedissonLock;
import com.openchat.common.exception.BusinessException;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.vo.CreateCallCommand;
import com.openchat.rtc.domain.vo.CallActionCommand;
import com.openchat.rtc.domain.vo.RtcCallParticipantView;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.webrtc.dao.RtcCallDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** 呼出前确认与替换；旧通话结束、新通话及可靠通知登记在同一数据库事务内。 */
@Service
@RequiredArgsConstructor
public class RtcDialService {
    private final RtcCallDao callDao;
    private final RtcCallService calls;
    private final RtcPermissionService permissions;
    private final RoomDao rooms;

    /** 按群发现唯一活动通话；查询无写入，不按本人的历史参与状态过滤。 */
    @Transactional(readOnly = true)
    public RtcCallView activeGroup(Long uid, Long roomId) {
        permissions.requireGroupMember(uid, roomId);
        return callDao.findByActiveKey("room:" + roomId)
                .map(call -> calls.get(call.callId(), uid))
                .filter(call -> !call.state().terminal())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RtcCallView> active(Long uid) {
        return callDao.findActiveByParticipant(uid).stream()
                .map(call -> calls.get(call.callId(), uid))
                .filter(call -> !call.state().terminal() && call.participants().stream().anyMatch(
                        member -> member.uid().equals(uid)
                                && (member.state() == RtcParticipantState.INVITED
                                || member.state() == RtcParticipantState.ACCEPTED)))
                .toList();
    }

    // 锁按呼出账号跨房间共享；锁切面在事务外，提交或回滚后才释放。
    @RedissonLock(prefixKey = "openchat:rtc:dial", key = "#uid")
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public RtcCallView create(Long uid, CreateCallCommand command, List<String> replaceCallIds) {
        var targetRoom = rooms.get(command.roomId());
        boolean group = targetRoom != null && targetRoom.getType() == RoomTypeEnum.GROUP;
        if (group) {
            permissions.requireGroupMember(uid, command.roomId());
        } else {
            permissions.requireCreate(uid, command);
        }
        var existing = callDao.findByCallerAndClientRequestId(uid, command.clientRequestId());
        if (existing.isPresent()) {
            RtcCallView call = calls.get(existing.get().callId(), uid);
            call.requireDevice(uid, command.device());
            return call;
        }

        Optional<RtcCallRecord> target = group
                ? callDao.findByActiveKey("room:" + command.roomId()) : Optional.empty();
        if (group && target.isEmpty()) permissions.requireCreate(uid, command);
        // 同群再次发起是加入，不能先把本人从目标通话移除，更不能结束最后一人的原通话。
        List<RtcCallRecord> active = callDao.findActiveByParticipant(uid).stream()
                .filter(call -> target.isEmpty() || !call.callId().equals(target.get().callId()))
                .toList();
        var confirmed = new HashSet<>(replaceCallIds == null ? List.<String>of() : replaceCallIds);
        if (active.stream().anyMatch(call -> !confirmed.contains(call.callId())))
            throw new BusinessException("当前已有通话正在进行，请确认是否强制呼出");
        for (RtcCallRecord call : active) {
            var room = rooms.get(call.roomId());
            if (room == null) throw new BusinessException("通话房间不存在");
            calls.endForRedial(call.callId(), uid, room.getType() == RoomTypeEnum.GROUP);
        }
        if (target.isPresent()) {
            return calls.accept(target.get().callId(), uid,
                    new CallActionCommand(command.clientRequestId(),
                            target.get().participantVersions().getOrDefault(uid, 1), command.device()));
        }
        RtcCallView created = calls.create(uid, command);
        // active_key 唯一约束处理跨节点同时创建；复用胜出的群通话并原子加入。
        if (group) {
            return calls.accept(created.callId(), uid,
                    new CallActionCommand(command.clientRequestId(),
                            created.participants().stream().filter(p -> p.uid().equals(uid))
                                    .map(RtcCallParticipantView::version).findFirst().orElse(1), command.device()));
        }
        // 既有创建入口可能返回同房间的其他呼叫；不能将它当成本次新呼出。
        var saved = callDao.findByCallerAndClientRequestId(uid, command.clientRequestId());
        if (saved.isEmpty() || !saved.get().callId().equals(created.callId()))
            throw new BusinessException("目标房间已有通话，请稍后重试");
        return created;
    }
}
