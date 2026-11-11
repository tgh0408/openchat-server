package com.openchat.chat.domain.vo.req.msg;

import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class TemporaryMsgReq {
    /** 5 视频，6 普通文件；缺省沿用已有视频客户端。 */
    @Builder.Default
    @NotNull
    private MessageTypeEnum targetType = MessageTypeEnum.VIDEO;
    @NotBlank
    @Size(max = 255)
    private String fileName;
    @NotNull @Min(1) @Max(6442450944L)
    private Long fileSize;
    @Min(1) @Max(86400000)
    private Long duration;
    @Min(1) @Max(1920)
    private Integer thumbWidth;
    @Min(1) @Max(1920)
    private Integer thumbHeight;
}
