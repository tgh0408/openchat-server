package com.openchat.webrtc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/** 当前业务参与者，联合主键为 room_id、uid；只通过完整条件操作。 */
@Data
@TableName("rtc_group_call_member")
public class RtcGroupCallMember {
    private Long roomId;
    private Long uid;
    private String callId;
    private String terminal;
    private String deviceId;
    private Date joinedAt;
}
