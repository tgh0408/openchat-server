package com.openchat.chat.controller;

import com.openchat.chat.domain.vo.req.ChatMessagePageReq;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.MessageDownloadReq;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.domain.vo.resp.MessageDownloadResp;
import com.openchat.chat.service.ChatService;
import com.openchat.chat.service.MessageDownloadService;
import com.openchat.chat.service.TemporaryMessageService;
import com.openchat.chat.domain.vo.req.TemporaryCompleteReq;
import com.openchat.chat.domain.vo.req.TemporaryStateReq;
import com.openchat.chat.domain.vo.req.TemporaryCancelReq;
import java.util.List;

import com.openchat.common.domain.enums.MsgSourceEnum;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.domain.vo.resp.CursorPageBaseResp;
import com.openchat.common.utils.RequestHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Set;

@RestController
@Tag(name = "消息相关接口")
@RequiredArgsConstructor
@RequestMapping("/capi/chat")
public class ChatController {
    final ChatService chatService;
    final TemporaryMessageService temporaryMessages;
    final MessageDownloadService messageDownloads;

    @PostMapping("/msg/download/url")
    @Operation(summary = "校验当前用户权限并获取消息附件的临时下载签名")
    public ApiResult<MessageDownloadResp> downloadUrl(@Valid @RequestBody MessageDownloadReq request) {
        return ApiResult.success(messageDownloads.authorize(
                request.getMsgId(), request.getRoomId(), RequestHolder.get().getUid()));
    }

    @PostMapping("/msg/state")
    @Operation(summary = "核对当前会话已缓存消息的最新状态，不推进已读")
    public ApiResult<List<ChatMessageResp>> messageState(@Valid @RequestBody TemporaryStateReq request) {
        List<ChatMessageResp> messages = chatService.getMsgState(request, RequestHolder.get().getUid());
        return ApiResult.success(messages);
    }

    @PostMapping("/msg/temporary/cancel")
    @Operation(summary = "取消本人尚未完成的文件或视频占位")
    public ApiResult<ChatMessageResp> cancelTemporary(@Valid @RequestBody TemporaryCancelReq request) {
        return ApiResult.success(temporaryMessages.cancel(request, RequestHolder.get().getUid()));
    }

    @PostMapping("/msg/temporary")
    @Operation(summary = "创建或恢复视频占位消息")
    public ApiResult<ChatMessageResp> createTemporary(@Valid @RequestBody ChatMessageReq request) {
        return ApiResult.success(temporaryMessages.create(request, RequestHolder.get().getUid()));
    }

    @PutMapping("/msg/temporary")
    @Operation(summary = "上传完成后原位替换视频占位")
    public ApiResult<Void> completeTemporary(@Valid @RequestBody TemporaryCompleteReq request) {
        temporaryMessages.complete(request, RequestHolder.get().getUid());
        return ApiResult.success();
    }

    @PostMapping("/msg/temporary/state")
    @Operation(summary = "补查当前会话缓存占位的最终状态")
    public ApiResult<List<ChatMessageResp>> temporaryState(@Valid @RequestBody TemporaryStateReq request) {
        List<ChatMessageResp> messages = temporaryMessages.state(request, RequestHolder.get().getUid());
        return ApiResult.success(messages);
    }

    @PostMapping("/msg")
    @Operation(summary = "发送消息")
    public ApiResult<ChatMessageResp> sendMsg(@Valid @RequestBody ChatMessageReq request) {
        Long uid = RequestHolder.get().getUid();
        chatService.sendMsg(request, uid, MsgSourceEnum.CLIENT);
        return ApiResult.success();
    }

    @PostMapping("/msg/page")
    @Operation(summary = "消息列表")
//    @FrequencyControl(time = 60, count = 60, target = FrequencyControl.Target.IP)
    public ApiResult<CursorPageBaseResp<ChatMessageResp>> getMsgPage(@Valid @RequestBody ChatMessagePageReq request) {
        CursorPageBaseResp<ChatMessageResp> msgPage = chatService.getMsgPage(request, RequestHolder.get().getUid());
        return ApiResult.success(msgPage);
    }



}
