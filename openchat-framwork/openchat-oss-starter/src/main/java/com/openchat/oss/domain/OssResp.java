package com.openchat.oss.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Description: 上传url请求出参
 * Author: 
 * Date: 2023-03-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OssResp {

    @Schema(description = "上传的临时url")
    private String uploadUrl;

    @Schema(description = "成功后能够下载的url")
    private String downloadUrl;

    @Schema(description = "截图时使用的名称为oss名称")
    private String fileName;
}
