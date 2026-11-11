package com.openchat.webrtc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import lombok.Getter;
import lombok.Setter;

/** 当前呼叫的协商快照，不包含媒体；终态和 SFU 回退时清除 SDP。 */
@Getter
@Setter
@TableName("rtc_media_session")
public class RtcMediaSession {
    @TableId(value = "call_id", type = IdType.INPUT)
    private String callId;
    /** 当前传输模式，持久化时使用枚举的字符串值。 */
    private RtcMediaTransportEnum transport;
    @Version
    private Integer version;
    private String offerId;
    private String offerSdp;
    private String answerSdp;
}
