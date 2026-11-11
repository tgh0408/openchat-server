package com.openchat.chat.domain.vo.req.msg;

import lombok.Data;
import jakarta.validation.constraints.*;

/** 录音完成并直传成功后才提交的语音条，不使用上传占位。 */
@Data
public class SoundMsgReq {
    @NotBlank @Size(max = 2048)
    private String content;
    @NotBlank @Size(max = 2048)
    private String uploadAddress;
    @NotBlank @Size(max = 255)
    private String fileName;
    @NotNull @Min(32044) @Max(3840044)
    private Long fileSize;
    @NotNull @Min(1) @Max(120)
    private Integer second;
}
