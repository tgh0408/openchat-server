package com.openchat.webrtc.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.rtc.autoconfigure.LiveKitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录后的媒体连接配置，沿用用户令牌、平台和设备鉴权。 */
@RestController
@RequestMapping("/capi/rtc")
@RequiredArgsConstructor
public class RtcConfigController {
    private final LiveKitProperties properties;

    /** 只下发客户端连接地址；未配置返回空地址，不影响客户端登录或普通聊天。 */
    @GetMapping("/config")
    public ApiResult<RtcConfig> config() {
        String serverUrl = properties.isConfigured() ? properties.getServerUrl().trim() : "";
        return ApiResult.success(new RtcConfig(serverUrl));
    }

    public record RtcConfig(String serverUrl) {}
}
