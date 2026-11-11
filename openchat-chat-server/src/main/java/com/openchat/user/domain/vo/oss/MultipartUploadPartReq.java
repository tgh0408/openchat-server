package com.openchat.user.domain.vo.oss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadPartReq {
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{64}")
    private String sessionKey;

    @NotBlank
    @Size(max = 512)
    private String uploadId;

    @NotNull
    @Min(1)
    @Max(384)
    private Integer partNumber;
}
