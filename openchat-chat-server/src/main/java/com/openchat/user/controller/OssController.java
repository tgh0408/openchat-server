package com.openchat.user.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.RequestHolder;
import com.openchat.oss.domain.OssResp;
import com.openchat.user.domain.vo.oss.MultipartUploadResp;
import com.openchat.user.service.MultipartUploadService;
import com.openchat.user.domain.vo.oss.MultipartUploadCompleteReq;
import com.openchat.user.domain.vo.oss.MultipartUploadInitReq;
import com.openchat.user.domain.vo.oss.MultipartUploadPartReq;
import com.openchat.user.domain.vo.oss.UploadUrlReq;
import com.openchat.user.service.OssService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * Description: oss控制层
 * Author: 
 * Date: 2023-06-20
 */
@RestController
@RequestMapping("/capi/oss")
@Tag(name = "oss相关接口")
@RequiredArgsConstructor
public class OssController {
    final OssService ossService;
    final MultipartUploadService multipartUploadService;

    @PostMapping("/upload/url")
    @Operation(summary = "获取临时上传链接")
    public ApiResult<OssResp> getUploadUrl(@Valid @RequestBody UploadUrlReq req) {
        return ApiResult.success(ossService.getUploadUrl(RequestHolder.get().getUid(), req));
    }

    @PostMapping("/multipart/initiate")
    public ApiResult<MultipartUploadResp> initiateMultipart(
            @Valid @RequestBody MultipartUploadInitReq req) {
        return ApiResult.success(multipartUploadService.initiate(
                RequestHolder.get().getUid(), req));
    }

    @PostMapping("/multipart/part-url")
    public ApiResult<Map<String, String>> getMultipartPartUrl(
            @Valid @RequestBody MultipartUploadPartReq req) {
        return ApiResult.success(multipartUploadService.partUrl(
                RequestHolder.get().getUid(), req));
    }

    @PostMapping("/multipart/complete")
    public ApiResult<MultipartUploadResp> completeMultipart(
            @Valid @RequestBody MultipartUploadCompleteReq req) {
        return ApiResult.success(multipartUploadService.complete(
                RequestHolder.get().getUid(), req));
    }

    @PostMapping("/multipart/abort")
    public ApiResult<Void> abortMultipart(@Valid @RequestBody MultipartUploadCompleteReq req) {
        multipartUploadService.abort(RequestHolder.get().getUid(), req);
        return ApiResult.success();
    }
}
