package com.openchat.user.domain.vo.oss;

import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class MultipartUploadInitReq {
    @NotNull
    @Positive
    private Long roomId;

    @NotBlank
    @Size(max = 64)
    private String clientMsgId;

    /** 5 视频，6 普通文件；未传时兼容已有视频客户端。 */
    @Builder.Default
    @NotNull
    private MessageTypeEnum targetType = MessageTypeEnum.VIDEO;

    @NotBlank
    @Size(max = 255)
    private String fileName;

    @NotNull
    @Positive
    @Max(6442450944L)
    private Long fileSize;

    @NotBlank
    @Pattern(regexp = "[a-f0-9]{64}")
    private String sha256;

}
