package com.openchat.webrtc.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.openchat.common.config.InterceptorConfig;
import com.openchat.common.exception.GlobalExceptionHandler;
import com.openchat.common.filter.RequestContextFilter;
import com.openchat.common.interceptor.BlackInterceptor;
import com.openchat.common.interceptor.PlusWebInvokeTimeInterceptor;
import com.openchat.common.interceptor.TokenInterceptor;
import com.openchat.common.properties.AuthProperties;
import com.openchat.common.utils.RequestHolder;
import com.openchat.common.utils.SpringUtils;
import com.openchat.user.domain.enums.TerminalEnum;
import com.openchat.user.service.LoginService;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.webrtc.dao.RtcWebhookEventDao;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import com.openchat.webrtc.mapper.RtcWebhookEventMapper;
import com.openchat.webrtc.service.RtcWebhookService;
import com.openchat.webrtc.service.RtcWebhookCallService;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 真实 SDK 验证测试签名和正文摘要，MockMvc 验证异常不会被全局处理器转换成成功。 */
public class LiveKitWebhookControllerTest {
    private static final String API_KEY = "test-webhook-key";
    private static final String SECRET = "test-webhook-secret-for-isolated-tests";
    private static final String BODY = """
            {
              "event": "participant_joined", "id": "event-1", "createdAt": "1794182400",
              "room": {"sid": "RM_1", "name": "openchat-call-call-1"},
              "participant": {"sid": "PA_1", "identity": "10", "name": "测试用户"}
            }
            """;
    private RtcWebhookService service;
    private MockMvc mvc;
    private AnnotationConfigWebApplicationContext context;

    @Before
    public void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebConfig.class);
        context.refresh();
        service = context.getBean(RtcWebhookService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestContextFilter(context.getBean(AuthProperties.class)))
                .build();
    }

    @After
    public void tearDown() {
        context.close();
        RequestHolder.remove();
    }

    @Test
    public void verifiesOriginalUtf8BodyWithoutUserSession() throws Exception {
        assertEquals(204, send(BODY, token(BODY)));
        var event = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(service).handle(event.capture());
        assertEquals("测试用户", event.getValue().getParticipant().getName());
        assertEquals(1794182400L, event.getValue().getCreatedAt());
        verifyNoInteractions(context.getBean(LoginService.class),
                context.getBean(BlackInterceptor.class), context.getBean(PlusWebInvokeTimeInterceptor.class));
        assertNull(RequestHolder.get());
    }

    @Test
    public void neighboringPathsStillRequireDeviceIdentity() throws Exception {
        for (String path : new String[]{"/capi/livekit/other", "/capi/livekit/callback/extra",
                "/capi/livekit/callback/", "/capi/user/userinfo", "/capi/user/public/login"}) {
            assertEquals(path, 403, mvc.perform(post(path)
                    .contentType("application/webhook+json")
                    .header("Authorization", token(BODY)).content(BODY))
                    .andReturn().getResponse().getStatus());
        }
        verifyNoInteractions(service, context.getBean(LoginService.class));
        assertNull(RequestHolder.get());
    }

    @Test
    public void neighboringPathStillRequiresUserTokenWithValidDevice() throws Exception {
        when(context.getBean(LoginService.class).getValidUid(null, "a".repeat(64), TerminalEnum.PC))
                .thenReturn(null);
        assertEquals(403, mvc.perform(post("/capi/livekit/other")
                .header("clientId", "test-client")
                .header("X-Device-Id", "a".repeat(64)))
                .andReturn().getResponse().getStatus());
        verify(context.getBean(LoginService.class)).getValidUid(null, "a".repeat(64), TerminalEnum.PC);
        verifyNoInteractions(service);
        assertNull(RequestHolder.get());
    }

    @Test
    public void callbackSupportsServletContextPath() throws Exception {
        assertEquals(204, mvc.perform(post("/openchat/capi/livekit/callback")
                .contextPath("/openchat")
                .contentType("application/webhook+json")
                .header("Authorization", token(BODY)).content(BODY.getBytes(StandardCharsets.UTF_8)))
                .andReturn().getResponse().getStatus());
        verify(service).handle(any());
    }

    @Test
    public void callbackCanMatchLaterWhitelistEntry() throws Exception {
        context.getBean(AuthProperties.class).setWhitePath(
                new String[]{"/unrelated-path", "/capi/livekit/callback"});
        assertEquals(204, send(BODY, token(BODY)));
        verify(service).handle(any());
    }

    @Test
    public void missingFilterWhitelistDoesNotBypassDeviceValidation() throws Exception {
        context.getBean(AuthProperties.class).setWhitePath(null);
        assertEquals(403, send(BODY, token(BODY)));
        verifyNoInteractions(service);
    }

    @Test
    public void verifiedCallbackRetriesStorageFailureAndAcknowledgesPersistedDuplicate() throws Exception {
        var calls = mock(RtcCallRepository.class);
        var call = mock(RtcCallRecord.class);
        when(call.participantUids()).thenReturn(List.of(10L, 20L));
        when(calls.findByCallId("call-1")).thenReturn(Optional.of(call));
        var mapper = mock(RtcWebhookEventMapper.class);
        when(mapper.insert(any(RtcWebhookEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("storage unavailable"))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("event_id"));
        var persistedService = new RtcWebhookService(calls, new RtcWebhookEventDao(mapper),
                mock(RtcWebhookCallService.class));
        mvc = MockMvcBuilders.standaloneSetup(new LiveKitWebhookController(
                new WebhookReceiver(API_KEY, SECRET), persistedService)).build();
        assertEquals(500, send(BODY, token(BODY)));
        assertEquals(204, send(BODY, token(BODY)));
        assertEquals(204, send(BODY, token(BODY)));
        var captured = ArgumentCaptor.forClass(RtcWebhookEvent.class);
        verify(mapper, times(3)).insert(captured.capture());
        for (var row : captured.getAllValues()) {
            assertEquals("event-1", row.getEventId());
            assertEquals("call-1", row.getCallId());
            assertEquals(Long.valueOf(10), row.getUid());
            assertEquals(Long.valueOf(1794182400L), row.getOccurredAtSeconds());
        }
        verifyNoMoreInteractions(mapper);
    }

    @Test
    public void rejectsMissingOrMalformedAuthorization() throws Exception {
        assertEquals(401, send(BODY, null));
        assertEquals(401, send(BODY, ""));
        assertEquals(401, send(BODY, "invalid-token"));
        verifyNoInteractions(service);
    }

    @Test
    public void rejectsChangedBodyEvenWhenJsonMeaningIsUnchanged() throws Exception {
        assertEquals(401, send(BODY + " ", token(BODY)));
        verifyNoInteractions(service);
    }

    @Test
    public void rejectsWrongKeySecretAndExpiredToken() throws Exception {
        assertEquals(401, send(BODY, token(BODY, "other-key", SECRET, Instant.now().plusSeconds(60))));
        assertEquals(401, send(BODY, token(BODY, API_KEY, "other-secret", Instant.now().plusSeconds(60))));
        assertEquals(401, send(BODY, token(BODY, API_KEY, SECRET, Instant.now().minusSeconds(60))));
        verifyNoInteractions(service);
    }

    @Test
    public void signedMalformedJsonReturnsBadRequest() throws Exception {
        String body = "{";
        assertEquals(400, send(body, token(body)));
        verifyNoInteractions(service);
    }

    @Test
    public void rejectsInvalidUtf8InsteadOfReplacingBytes() throws Exception {
        int status = mvc.perform(post("/capi/livekit/callback")
                        .contentType("application/webhook+json")
                        .header("Authorization", token(BODY))
                        .content(new byte[]{(byte) 0xc3, (byte) 0x28}))
                .andReturn().getResponse().getStatus();
        assertEquals(400, status);
        verifyNoInteractions(service);
    }

    @Test
    public void processingFailureRemainsRetryableHttpFailure() throws Exception {
        doThrow(new IllegalStateException("storage unavailable")).when(service).handle(any());
        assertEquals(500, send(BODY, token(BODY)));
    }

    @Test
    public void invalidEventDoesNotBecomeHttpSuccess() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(service).handle(any());
        assertEquals(400, send(BODY, token(BODY)));
    }

    @Test
    public void emptyBodyReturnsBadRequestDespiteGlobalAdvice() throws Exception {
        assertEquals(400, send("", token("")));
        verifyNoInteractions(service);
    }

    private int send(String body, String authorization) throws Exception {
        var request = post("/capi/livekit/callback")
                .contentType("application/webhook+json")
                .content(body.getBytes(StandardCharsets.UTF_8));
        if (authorization != null) request.header("Authorization", authorization);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private String token(String body) throws Exception {
        return token(body, API_KEY, SECRET, Instant.now().plusSeconds(60));
    }

    private String token(String body, String key, String secret, Instant expiresAt) throws Exception {
        String digest = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        return JWT.create().withIssuer(key).withExpiresAt(expiresAt)
                .withClaim("sha256", digest).sign(Algorithm.HMAC256(secret));
    }

    /** 只装配 MVC 和真实鉴权入口，不加载数据库、MQ 或业务应用配置。 */
    @Configuration
    @EnableWebMvc
    @Import({InterceptorConfig.class, GlobalExceptionHandler.class, ProtectedController.class})
    public static class WebConfig {
        @Bean
        public RtcWebhookService webhookService() {
            return mock(RtcWebhookService.class);
        }

        @Bean
        public LiveKitWebhookController webhookController(RtcWebhookService service) {
            return new LiveKitWebhookController(new WebhookReceiver(API_KEY, SECRET), service);
        }

        @Bean
        public LoginService loginService() {
            return mock(LoginService.class);
        }

        @Bean
        public TokenInterceptor tokenInterceptor(LoginService service) {
            return new TokenInterceptor(service);
        }

        @Bean
        public BlackInterceptor blackInterceptor() {
            return mock(BlackInterceptor.class);
        }

        @Bean
        public PlusWebInvokeTimeInterceptor timingInterceptor() {
            return mock(PlusWebInvokeTimeInterceptor.class);
        }

        @Bean
        public AuthProperties authProperties() {
            AuthProperties properties = new AuthProperties();
            properties.setClientId(Map.of(TerminalEnum.PC, "test-client"));
            properties.setWhitePath(new String[]{"/capi/livekit/callback"});
            return properties;
        }

        @Bean
        public static SpringUtils springUtils() {
            return new SpringUtils();
        }

        @Bean
        public JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @RestController
    public static class ProtectedController {
        @PostMapping("/capi/livekit/other")
        public void other() {
            fail("Unauthenticated request reached protected controller");
        }
    }
}
