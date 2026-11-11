package com.openchat.webrtc.service;

import com.openchat.rtc.autoconfigure.LiveKitProperties;
import com.openchat.webrtc.dao.RtcCallDao;
import com.sun.net.httpserver.HttpServer;
import livekit.LivekitModels;
import livekit.LivekitRoom;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 真实 LiveKit SDK 访问本机临时 HTTP 服务，不访问外部 LiveKit。 */
public class RtcWebhookRoomQueryTest {
    private HttpServer server;
    private RtcMediaCleanupService service;
    private volatile byte[] responseBody = LivekitRoom.ListRoomsResponse.getDefaultInstance().toByteArray();
    private volatile int responseCode = 200;
    private volatile List<String> requestedRooms;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedRooms = LivekitRoom.ListRoomsRequest.parseFrom(
                    exchange.getRequestBody().readAllBytes()).getNamesList();
            byte[] body = responseBody;
            exchange.getResponseHeaders().set("Content-Type", "application/protobuf");
            exchange.sendResponseHeaders(responseCode, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        var properties = new LiveKitProperties();
        properties.setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setApiSecret("test-secret-for-isolated-room-query");
        service = new RtcMediaCleanupService(mock(RtcCallDao.class), properties);
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void emptyAuthoritativeRoomListConfirmsClosure() {
        assertTrue(service.isRoomClosed("call", "RM_1"));
        assertEquals(List.of("openchat-call-call"), requestedRooms);
    }

    @Test
    public void recreatedRoomRejectsOldFinishedEvent() {
        responseBody = room("RM_2");
        assertFalse(service.isRoomClosed("call", "RM_1"));
    }

    @Test
    public void sameRoomStillPresentRequiresRedelivery() {
        responseBody = room("RM_1");
        assertThrows(IllegalStateException.class, () -> service.isRoomClosed("call", "RM_1"));
    }

    @Test
    public void failedOrMalformedResponseDoesNotMeanRoomIsGone() {
        responseCode = 503;
        assertThrows(IllegalStateException.class, () -> service.isRoomClosed("call", "RM_1"));
        responseCode = 200;
        responseBody = new byte[]{0};
        assertThrows(IllegalStateException.class, () -> service.isRoomClosed("call", "RM_1"));
    }

    private byte[] room(String sid) {
        return LivekitRoom.ListRoomsResponse.newBuilder().addRooms(
                LivekitModels.Room.newBuilder().setName("openchat-call-call").setSid(sid))
                .build().toByteArray();
    }
}
