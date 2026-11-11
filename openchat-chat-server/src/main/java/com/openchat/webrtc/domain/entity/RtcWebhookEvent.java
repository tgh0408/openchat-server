package com.openchat.webrtc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 已验签的 LiveKit 媒体事件；只追加，不以到达顺序覆盖业务或成员状态。 */
@Data
@TableName("rtc_webhook_event")
public class RtcWebhookEvent {
    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;
    private String callId;
    private String eventType;
    private String roomSid;
    private String participantSid;
    private Long uid;
    /** LiveKit 事件发生时间，保留原始 Unix 秒，独立于本机接收时间。 */
    private Long occurredAtSeconds;
    /** 无效或缺失的房间创建时间为空，不推算媒体连接时间。 */
    private Long roomCreatedAtSeconds;
    private Date receivedAt;
}
