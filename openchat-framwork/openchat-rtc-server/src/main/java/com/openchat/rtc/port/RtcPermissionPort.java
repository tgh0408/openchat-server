package com.openchat.rtc.port;

import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.vo.CreateCallCommand;

/**
 * RTC 核心所需的业务权限端口，由聊天服务实现。
 */
public interface RtcPermissionPort {
    RtcCallParticipants requireCreate(Long callerUid, CreateCallCommand command);

    void requireParticipant(RtcCallRecord call, Long uid);

    /** 群聊允许当前有效成员查看和加入，单聊仍要求属于原通话。 */
    void requireJoin(RtcCallRecord call, Long uid);

    /** 由业务房间类型判断群聊，不能按本次邀请人数推断。 */
    boolean isGroup(RtcCallRecord call);
}
