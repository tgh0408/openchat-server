package com.openchat.user.domain.vo.oss;

import com.openchat.common.domain.enums.OssSceneEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


/**
 * Description: 上传url请求入参
 * Author: 
 * Date: 2023-03-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadUrlReq {
    @Schema(description = "文件名（带后缀）")
    @NotBlank
    private String fileName;

    /**
     * @see OssSceneEnum
     */
    @Schema(description = "上传场景")
    @NotNull
    private OssSceneEnum scene;
    /** 视频封面签名绑定原占位；普通图片不传。 */
    private Long roomId;
    private String clientMsgId;
}
