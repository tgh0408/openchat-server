package com.openchat.chat.domain.vo.req;

import com.openchat.chat.domain.vo.req.msg.FileMsgReq;
import com.openchat.chat.domain.vo.req.msg.VideoMsgReq;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** 完成同一条上传占位；身份始终取当前登录用户。 */
@Data
public class TemporaryCompleteReq {
    @NotNull @Min(1)
    private Long roomId;
    @NotNull @Min(1)
    private Long msgId;
    @NotBlank @Size(max = 64)
    private String clientMsgId;
    @Valid
    private VideoMsgReq body;
    @Valid
    private FileMsgReq fileBody;
}
