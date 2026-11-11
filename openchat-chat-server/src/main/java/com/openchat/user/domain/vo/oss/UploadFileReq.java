package com.openchat.user.domain.vo.oss;


import com.openchat.common.domain.enums.OssSceneEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadFileReq {
    @Schema(description = "桶")
    @NotBlank
    private String bucket;

    @Schema(description = "文件路径以及名字")
    @NotBlank
    private String filePath;

    @Schema(description = "文件输入流")
    @NotBlank
    private InputStream stream;

    @Schema(description = "文件输入流")
    @NotBlank
    private OssSceneEnum scene;
}
