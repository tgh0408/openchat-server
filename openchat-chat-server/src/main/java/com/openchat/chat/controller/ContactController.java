package com.openchat.chat.controller;


import com.openchat.chat.domain.vo.req.AddContactReq;
import com.openchat.chat.domain.vo.req.DeleteContactReq;
import com.openchat.chat.domain.vo.req.ContactReadReq;
import com.openchat.chat.domain.vo.req.ContactNotDisturbReq;
import com.openchat.chat.domain.vo.req.HideContactReq;
import com.openchat.chat.service.ContactReadService;
import com.openchat.chat.domain.vo.resp.ChatRoomResp;
import com.openchat.chat.service.ContactService;
import com.openchat.chat.service.RoomAppService;
import com.openchat.common.annotation.FrequencyControl;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.RequestHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>1:man 2:woman
 * 会话列表 前端控制器
 * </p>
 *
 * @author TGH
 * @since 2023-12-14
 */
@RestController
@RequestMapping("/capi/chat")
@RequiredArgsConstructor
@Tag(name = "聊天室相关接口")
public class ContactController {
    final ContactService contactService;
    final ContactReadService contactReadService;

    @PostMapping("/contact/read")
    @Operation(summary = "确认实际已读位置")
    @FrequencyControl(time = 60, count = 30, target = FrequencyControl.Target.UID)
    public ApiResult<Long> acknowledgeRead(@Valid @RequestBody ContactReadReq request) {
        return ApiResult.success(contactReadService.acknowledge(
                RequestHolder.get().getUid(), request.getRoomId(), request.getLastReadMsgId()));
    }

    @PostMapping("/contact/list")
    @Operation(summary = "全量同步当前用户可见会话列表")
    @FrequencyControl(time = 4, count = 2, target = FrequencyControl.Target.UID)
    public ApiResult<List<ChatRoomResp>> getRoomList() {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(contactService.getContactList(uid));
    }

    @PostMapping("/contact/info/{roomId}")
    @Operation(summary = "获取会话详情")
    public ApiResult<ChatRoomResp> getContactInfo(@NotNull @PathVariable Long roomId){
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(contactService.getContactInfo(uid,roomId));
    }

    @PostMapping("/contact/add")
    @Operation(summary = "新增会话列表")
    public ApiResult<Void> addContact(@Valid @RequestBody AddContactReq request) {
        Long uid = RequestHolder.get().getUid();
        contactService.addContact(request, uid);
        return ApiResult.success();
    }

    @PostMapping("/contact/remove")
    @Operation(summary = "删除会话列表")
    public ApiResult<Void> deleteContact(@Valid @RequestBody DeleteContactReq request) {
        Long uid = RequestHolder.get().getUid();
        contactService.deleteContact(request, uid);
        return ApiResult.success();
    }

    @PostMapping("/contact/notDisturb")
    @Operation(summary = "设置当前用户的会话消息免打扰")
    public ApiResult<Void> changeNotDisturb(@Valid @RequestBody ContactNotDisturbReq request) {
        contactService.changeNotDisturb(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PostMapping("/contact/hide")
    @Operation(summary = "隐藏当前用户的会话")
    public ApiResult<Void> hideContact(@Valid @RequestBody HideContactReq request) {
        Long uid = RequestHolder.get().getUid();
        contactService.hideContact(request, uid);
        return ApiResult.success();
    }
}

