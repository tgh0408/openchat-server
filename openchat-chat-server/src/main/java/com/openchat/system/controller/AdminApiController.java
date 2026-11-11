package com.openchat.system.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.system.domain.dto.AdminPrincipal;
import com.openchat.system.domain.vo.req.AdminLoginReq;
import com.openchat.system.domain.vo.resp.AdminLoginResp;
import com.openchat.system.domain.vo.resp.AdminReleaseResp;
import com.openchat.system.exception.AdminApiException;
import com.openchat.system.intercepter.AdminWebInterceptor;
import com.openchat.system.service.AdminAuthService;
import com.openchat.system.service.ClientReleaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 独立 Dashboard 的 JSON API，保留公开客户端更新协议。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/admin")
public class AdminApiController {
    private final AdminAuthService authService;
    private final ClientReleaseService releaseService;

    @PostMapping("/login")
    public ApiResult<AdminLoginResp> login(@Valid @RequestBody AdminLoginReq request,
                                           HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResult.success(authService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResult<AdminPrincipal> me(HttpServletRequest request) {
        return ApiResult.success(AdminWebInterceptor.requirePrincipal(request));
    }

    @GetMapping("/releases")
    public ApiResult<List<AdminReleaseResp>> releases() {
        return ApiResult.success(releaseService.listAdminReleases());
    }

    @PostMapping(value = "/release/publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Void> publish(@RequestParam("clientFile") MultipartFile file,
                                   @RequestParam(defaultValue = "") String updateInfo) {
        if (updateInfo.length() > 20000) {
            throw new AdminApiException(400, "更新说明不能超过 20000 个字符");
        }
        releaseService.publishUploaded(file, updateInfo);
        return ApiResult.success();
    }


}
