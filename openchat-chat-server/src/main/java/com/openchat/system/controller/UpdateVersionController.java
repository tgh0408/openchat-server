package com.openchat.system.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.system.exception.AdminApiException;
import com.openchat.system.service.ClientReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/**
 * 客户端更新公开接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/capi/update")
public class UpdateVersionController {
    private final ClientReleaseService clientReleaseService;

    @GetMapping("/public/latest")
    public ApiResult<ClientReleaseService.ClientUpdateResp> latest() {
        return ApiResult.success(clientReleaseService.getLatest(true));
    }

    @GetMapping("/public/releases/{releaseId}")
    public ApiResult<String> download(@PathVariable Long releaseId) {
        return ApiResult.success(clientReleaseService.getPublicDownloadUrl(releaseId));
    }
}
