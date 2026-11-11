package com.openchat.webrtc.service;

import com.openchat.rtc.autoconfigure.LiveKitProperties;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.transaction.annotation.SecureInvoke;
import com.openchat.webrtc.constant.LiveKitRoomKey;
import com.openchat.webrtc.dao.RtcCallDao;
import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 按不可复用的 callId 清理终态 SFU 房间，副作用沿用跨节点可靠调用重试。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtcMediaCleanupService {
    private final RtcCallDao calls;
    private final LiveKitProperties properties;

    @SecureInvoke(maxRetryTimes = 10)
    public void closeRoom(String callId) {
        var call = calls.findByCallId(callId).orElseThrow();
        if (!call.state().terminal()) throw new IllegalStateException("不能清理活动通话媒体");
        try {
            var client = client();
            Call<Void> request = client.deleteRoom(LiveKitRoomKey.roomKey + callId);
            request.timeout().timeout(15, TimeUnit.SECONDS);
            Response<Void> response = request.execute();
            try (ResponseBody errorBody = response.errorBody()){
                if (errorBody != null) errorBody.close();
                if (!response.isSuccessful() && response.code() != 404)
                    throw new IllegalStateException("媒体清理 HTTP 状态 " + response.code());
                log.info("event={} callId={} status={}", RtcAuditEvent.RTC_MEDIA_CLEANUP_COMPLETED, callId, response.code());
            }
        } catch (Exception exception) {
            log.warn("event={} callId={} exceptionType={}",
                    RtcAuditEvent.RTC_MEDIA_CLEANUP_FAILED, callId, exception.getClass().getSimpleName());
            throw new IllegalStateException("RTC 媒体房间清理失败", exception);
        }
    }

    /** 结束回调可能晚于同名房间重建；查询失败不能当作房间不存在。 */
    public boolean isRoomClosed(String callId, String roomSid) {
        try {
            var request = client().listRooms(List.of(LiveKitRoomKey.roomKey + callId));
            request.timeout().timeout(15, TimeUnit.SECONDS);
            var response = request.execute();
            try (ResponseBody error = response.errorBody()) {
                if (!response.isSuccessful() || response.body() == null)
                    throw new IllegalStateException("LiveKit 房间查询失败");
                if (response.body().stream().anyMatch(room -> roomSid.equals(room.getSid())))
                    throw new IllegalStateException("LiveKit 房间结束尚未确认");
                return response.body().isEmpty();
            }
        } catch (Exception exception) {
            // SDK 异常可能包含请求信息，只向上层暴露固定错误。
            throw new IllegalStateException("无法确认 LiveKit 房间已结束");
        }
    }

    private RoomServiceClient client() throws java.net.URISyntaxException {
        URI endpoint = URI.create(properties.getServerUrl());
        String scheme = switch (endpoint.getScheme()) {
            case "ws" -> "http";
            case "wss" -> "https";
            case "http", "https" -> endpoint.getScheme();
            default -> throw new IllegalStateException("LiveKit 服务地址无效");
        };
        URI http = new URI(scheme, null, endpoint.getHost(), endpoint.getPort(),
                endpoint.getPath(), null, null);
        return RoomServiceClient.createClient(http.toString(), properties.getApiKey(), properties.getApiSecret());
    }
}
