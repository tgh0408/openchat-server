package com.openchat.webrtc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.openchat.rtc.domain.RtcParticipantState;
import lombok.Data;
import java.util.Date;

/** 每通每人成员状态，联合主键 call_id、uid；成员版本独立于整通版本。 */
@Data
@TableName("rtc_group_call_member_history")
public class RtcGroupCallMemberHistory {
    private String callId;
    private Long uid;
    private Long roomId;
    private RtcParticipantState state;
    private String terminal;
    private String deviceId;
    @Version
    private Integer version;
    private Date invitedAt;
    private Date acceptedAt;
    private Date leftAt;
    private Date createdAt;
    private Date updatedAt;
}
