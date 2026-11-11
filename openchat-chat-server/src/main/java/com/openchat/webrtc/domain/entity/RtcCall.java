package com.openchat.webrtc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.openchat.rtc.domain.RtcCallState;
import lombok.Data;

import java.util.Date;

/**
 * RTC 呼叫记录。
 */
@Data
@TableName("rtc_call")
public class RtcCall {
    @TableId("call_id")
    private String callId;
    private Long roomId;
    private Long callerUid;
    private Long calleeUid;
    private String mediaMode;
    private RtcCallState state;
    @Version
    private Integer version;
    private String clientRequestId;
    private String activeKey;
    private Date expiresAt;
    private String endReason;
    private Date createdAt;
    private Date updatedAt;
    private Date endedAt;
    private Date acceptedAt;
    private Date connectedAt;
    private Long durationSeconds;
    private Long endedByUid;
    private Long summaryMessageId;
    /** 单聊双方设备归属；群通话固定为空，设备保存在成员表。 */
    private String participantDevices;
}
