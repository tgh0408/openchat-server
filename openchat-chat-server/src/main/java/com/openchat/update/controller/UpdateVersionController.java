package com.openchat.update.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.update.domain.vo.resp.ClientUpdateResp;
import com.openchat.update.service.ClientUpdateService;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户端通过原聊天 API 基址检查更新和查询公开下载地址。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/capi/update")
public class UpdateVersionController {
    private final ClientUpdateService service;

    @GetMapping( "/public/latest/client")
    public ApiResult<ClientUpdateResp> latest() {
        return ApiResult.success(service.getLatest(TerminalEnum.PC));
    }

    @GetMapping("/public/latest/android")
    public ApiResult<ClientUpdateResp> latestAndroid() {
        return ApiResult.success(service.getLatest(TerminalEnum.PHONE));
    }

    @GetMapping("/public/releases/client/{releaseId}")
    public ApiResult<String> download(@PathVariable Long releaseId) {
        return ApiResult.success(service.getPublicDownloadUrl(releaseId, TerminalEnum.PC));
    }

    @GetMapping("/public/releases/android/{releaseId}")
    public ApiResult<String> downloadAndroid(@PathVariable Long releaseId) {
        return ApiResult.success(service.getPublicDownloadUrl(releaseId, TerminalEnum.PHONE));
    }
}
