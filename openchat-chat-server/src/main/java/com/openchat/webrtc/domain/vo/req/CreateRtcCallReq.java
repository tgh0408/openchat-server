package com.openchat.webrtc.domain.vo.req;

import com.openchat.rtc.domain.RtcMediaMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建 RTC 呼叫请求。
 */
@Data
public class CreateRtcCallReq {
    @NotNull
    @Positive
    private Long roomId;
    @NotNull
    private RtcMediaMode mediaMode;
    @NotBlank
    private String clientRequestId;
    /**
     * 群聊中被邀请的成员 UID；不包含当前发起人。为空时兼容旧调用，邀请全体群成员。
     */
    private List<@Positive Long> participantUids;
    /** 用户确认可结束的通话快照；缺省或空列表不允许结束任何现有通话。 */
    @Size(max = 100)
    private List<@NotBlank @Size(max = 128) String> replaceCallIds;
}
