package com.openchat.user.domain.vo.oss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadCompleteReq {
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{64}")
    private String sessionKey;

    @NotBlank
    @Size(max = 512)
    private String uploadId;
}
