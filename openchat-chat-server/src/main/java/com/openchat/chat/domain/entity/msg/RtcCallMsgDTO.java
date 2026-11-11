package com.openchat.chat.domain.entity.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * RTC 通话摘要消息的不可变快照。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RtcCallMsgDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String callId;
    private String mediaMode;
    private String state;
    private String endReason;
    private Long endedByUid;
    private Date acceptedAt;
    private Date endedAt;
    private Long durationSeconds;
}
