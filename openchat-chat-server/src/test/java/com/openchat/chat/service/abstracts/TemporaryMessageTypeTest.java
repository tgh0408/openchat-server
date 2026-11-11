package com.openchat.chat.service.abstracts;

import cn.hutool.core.bean.BeanUtil;
import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.msg.TemporaryMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.msg.*;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.service.ChatService;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.chat.service.TemporaryMessagePublisher;
import com.openchat.chat.service.TemporaryMessageService;
import com.openchat.common.config.JacksonConfig;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 不启动应用或访问外部服务，验证嵌套占位消息按协议码转换及原有字段校验。 */
public class TemporaryMessageTypeTest {
    private TemporaryMsgHandler handler;
    private JsonMapper mapper;
    private final RoomMessage room = RoomMessage.builder()
            .roomId(10L).roomType(RoomTypeEnum.FRIEND).userId(1L).build();

    @Before
    public void setUp() {
        handler = new TemporaryMsgHandler();
        ReflectionTestUtils.setField(handler, "bodyClass", TemporaryMsgReq.class);
        var builder = JsonMapper.builder();
        new JacksonConfig().longToStringCustomizer().customize(builder);
        mapper = builder.build();
        ReflectionTestUtils.setField(handler, "jsonMapper", mapper);
    }

    @Test
    public void nestedBodyConvertsEveryMessageTypeByProtocolCodeInsteadOfOrdinal() {
        for (MessageTypeEnum type : MessageTypeEnum.values()) {
            TemporaryMsgReq converted = ReflectionTestUtils.invokeMethod(
                    handler, "toBean", Map.of("targetType", type.getType()));
            assertNotNull(converted);
            assertEquals(type.name(), type, converted.getTargetType());
        }
    }

    @Test
    public void videoRequestSurvivesHttpBodyConversionAndIdempotencyComparison() {
        ChatMessageReq request = mapper.readValue("""
                {
                  "roomId": "10",
                  "clientMsgId": "video-type-test",
                  "msgType": 3,
                  "body": {
                    "targetType": 5,
                    "fileName": "clip.mp4",
                    "fileSize": "4096",
                    "duration": "1000",
                    "thumbWidth": 640,
                    "thumbHeight": 360
                  }
                }
                """, ChatMessageReq.class);
        assertEquals(MessageTypeEnum.TEMP, request.getMsgType());
        TemporaryMsgReq validated = handler.validatedBody(request, room, 1L);
        TemporaryMsgReq compared = handler.validatedBody(request, room, 1L);
        assertEquals(MessageTypeEnum.VIDEO, validated.getTargetType());
        assertEquals(validated, compared);
        assertEquals(Long.valueOf(4096), validated.getFileSize());
        assertEquals(Long.valueOf(1000), validated.getDuration());
    }

    @Test
    public void ordinaryFileKeepsItsTypeAndDoesNotRequireVideoFields() {
        Map<String, Object> body = new HashMap<>(Map.of(
                "targetType", 6, "fileName", "archive.zip", "fileSize", "4096"));
        assertEquals(MessageTypeEnum.FILE, validate(body).getTargetType());
        body.put("duration", "1000");
        BusinessException error = assertThrows(BusinessException.class, () -> validate(body));
        assertEquals("普通文件占位不接受视频字段", error.getMessage());
    }

    @Test
    public void videoStillRequiresDurationAndPosterDimensions() {
        Map<String, Object> body = new HashMap<>(Map.of(
                "targetType", 5, "fileName", "clip.mp4", "fileSize", "4096"));
        BusinessException error = assertThrows(BusinessException.class, () -> validate(body));
        assertEquals("视频占位缺少时长或封面尺寸", error.getMessage());
        body.put("duration", "1000");
        body.put("thumbWidth", 640);
        body.put("thumbHeight", 360);
        assertEquals(MessageTypeEnum.VIDEO, validate(body).getTargetType());
        body.remove("targetType");
        assertEquals(MessageTypeEnum.VIDEO, validate(body).getTargetType());
        body.put("targetType", null);
        assertThrows(BusinessException.class, () -> validate(body));
    }


    @Test
    public void unknownCodesCannotFallBackToAnOrdinal() {
        for (int code : new int[]{-1, 0, 12}) {
            Map<String, Object> body = Map.of("targetType", code,
                    "fileName", "clip.mp4", "fileSize", "4096",
                    "duration", "1000", "thumbWidth", 640, "thumbHeight", 360);
            assertThrows(RuntimeException.class, () -> validate(body));
        }
    }

    @Test
    public void idempotentVideoPlaceholderUsesTheValidatedMetadata() {
        RoomAccessService access = mock(RoomAccessService.class);
        TemporaryMessageDao dao = mock(TemporaryMessageDao.class);
        ChatService chat = mock(ChatService.class);
        TemporaryMessagePublisher publisher = mock(TemporaryMessagePublisher.class);
        Room storedRoom = new Room();
        storedRoom.setId(10L);
        storedRoom.setType(RoomTypeEnum.FRIEND);
        when(access.requireCanSend(1L, 10L)).thenReturn(storedRoom);
        Message message = new Message();
        message.setId(20L);
        message.setType(MessageTypeEnum.TEMP);
        message.setStatus(YesOrNoEnum.NO);
        MessageExtra extra = new MessageExtra();
        extra.setTemporary(TemporaryMsgDTO.builder().targetType(MessageTypeEnum.VIDEO)
                .fileName("clip.mp4").size(4096L).duration(1000L)
                .thumbWidth(640).thumbHeight(360).build());
        message.setExtra(extra);
        when(dao.find(RoomTypeEnum.FRIEND, 10L, 1L, "message-type-test")).thenReturn(message);
        ChatMessageResp response = new ChatMessageResp();
        when(chat.getMsgResp(message, 1L)).thenReturn(response);
        TemporaryMessageService service = new TemporaryMessageService(
                access, dao, handler, null, null, chat, null, publisher, null);
        ChatMessageReq request = request(Map.of("targetType", 5,
                "fileName", "clip.mp4", "fileSize", "4096",
                "duration", "1000", "thumbWidth", 640, "thumbHeight", 360));
        assertSame(response, service.create(request, 1L));
        verify(dao).find(RoomTypeEnum.FRIEND, 10L, 1L, "message-type-test");
        verifyNoMoreInteractions(dao);
        verifyNoInteractions(publisher);
    }

    @Test
    public void otherMessageBodiesKeepScalarAndListConversions() {
        Map<Class<?>, Map<String, Object>> cases = Map.of(
                TextMsgReq.class, Map.of("content", "hello", "atUidList", List.of("9007199254740993")),
                ImgMsgReq.class, Map.of("fileName", "image.jpg", "fileSize", "4096"),
                VideoMsgReq.class, Map.of("fileName", "clip.mp4", "duration", "1000", "thumbWidth", 640),
                FileMsgReq.class, Map.of("fileName", "archive.zip", "fileSize", "6442450944"),
                SoundMsgReq.class, Map.of("fileName", "voice.wav", "fileSize", "32044", "second", 1),
                VoiceMsgReq.class, Map.of("content", "voice", "fileSize", "4096"),
                EmojiMsgReq.class, Map.of("emojiId", "9007199254740993"),
                RecallMsgReq.class, Map.of("msgId", "9007199254740993"));
        cases.forEach((type, body) -> {
            ReflectionTestUtils.setField(handler, "bodyClass", type);
            Object converted = ReflectionTestUtils.invokeMethod(handler, "toBean", body);
            assertEquals(type.getSimpleName(), BeanUtil.toBean(body, type), converted);
        });
    }

    @Test
    public void jacksonProtocolAndTypedBodiesRemainUnchanged() {
        for (MessageTypeEnum type : MessageTypeEnum.values()) {
            assertEquals(type.getType().toString(), mapper.writeValueAsString(type));
            assertEquals(type, mapper.readValue(type.getType().toString(), MessageTypeEnum.class));
        }
        TemporaryMsgReq typed = TemporaryMsgReq.builder()
                .targetType(MessageTypeEnum.FILE).fileName("archive.zip").fileSize(4096L).build();
        assertSame(typed, handler.validatedBody(request(typed), room, 1L));
    }

    private TemporaryMsgReq validate(Map<String, Object> body) {
        return handler.validatedBody(request(body), room, 1L);
    }

    private ChatMessageReq request(Object body) {
        return ChatMessageReq.builder().roomId(10L).msgType(MessageTypeEnum.TEMP)
                .clientMsgId("message-type-test").body(body).build();
    }
}
