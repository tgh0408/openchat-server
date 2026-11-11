package com.openchat.chat.domain.vo.req;

import lombok.Data;
import jakarta.validation.constraints.*;

/** clientMsgId 允许占位创建响应丢失时仍取消同一条上传；无占位时先重试创建。 */
@Data
public class TemporaryCancelReq {
    @NotNull @Min(1)
    private Long roomId;
    @NotBlank @Size(max = 64)
    private String clientMsgId;
}
