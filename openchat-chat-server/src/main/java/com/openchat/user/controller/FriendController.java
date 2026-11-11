package com.openchat.user.controller;

import com.openchat.chat.service.ContactService;
import com.openchat.common.domain.vo.req.PageBaseReq;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.domain.vo.resp.CursorPageBaseResp;
import com.openchat.common.domain.vo.resp.FriendResp;
import com.openchat.common.domain.vo.resp.PageBaseResp;
import com.openchat.common.utils.RequestHolder;
import com.openchat.user.domain.vo.req.*;
import com.openchat.user.domain.vo.resp.*;
import com.openchat.user.service.FriendService;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/capi/user/friend")
@Tag(name = "好友相关接口")
@Slf4j
@RequiredArgsConstructor
public class FriendController {
    final FriendService friendService;
    final ContactService contactService;

    @PostMapping(value = "/remark", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "保存本人对好友的备注、标签、电话和描述")
    public ApiResult<FriendRemarkResp> remark(@Valid @RequestBody FriendRemarkReq request) {
        return ApiResult.success(friendService.saveRemark(RequestHolder.get().getUid(), request));
    }

    @GetMapping("/check")
    @Operation(summary = "批量判断是否是自己好友")
    public ApiResult<FriendCheckResp> check(@Valid @RequestBody FriendCheckReq friendCheckReq) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.check(uid, friendCheckReq));
    }

    @PostMapping("/apply/page")
    @Operation(summary = "好友申请列表")
    public ApiResult<PageBaseResp<FriendApplyResp>> page(@Valid @RequestBody PageBaseReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.pageApplyFriend(uid, request));
    }

    @GetMapping("/apply/unread")
    @Operation(summary = "查询有多少未读申请")
    public ApiResult<List<FriendUnRead>> getUnreadApply() {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.selectUnreadApply(uid));
    }

    @GetMapping("/apply/read/{applyId}")
    @Operation(summary = "查阅apply申请")
    public ApiResult<Void> applyId(@Valid @NotNull @PathVariable("applyId") Long applyId) {
        Long uid = RequestHolder.get().getUid();
        friendService.readApply(uid,applyId);
        return ApiResult.success();
    }

    @PostMapping("/apply")
    @Operation(summary = "申请好友")
    public ApiResult<FriendApplyResultResp> apply(@Valid @RequestBody FriendApplyReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.apply(uid, request));
    }

    @PostMapping("/replay")
    @Operation(summary = "回复申请好友")
    public ApiResult<Void> replyApply(@Valid @RequestBody FriendReplyReq request) {
        friendService.replyApply(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PostMapping("/agreeApply")
    @Operation(summary = "审批同意")
    public ApiResult<Void> applyApprove(@Valid @RequestBody FriendApproveReq request) {
        Long uid = RequestHolder.get().getUid();
        friendService.applyApprove(uid, request);
        return ApiResult.success();
    }

    @PostMapping("/refuseApply")
    @Operation(summary = "拒绝好友申请")
    public ApiResult<Void> applyRefuse(@Valid @RequestBody FriendRefuseReq request) {
        Long uid = RequestHolder.get().getUid();
        friendService.applyRefuse(uid, request);
        return ApiResult.success();
    }

    @PostMapping("/del/{roomId}")
    @Operation(summary = "删除好友")
    public ApiResult<Void> delete(@PathVariable("roomId") @Positive @NotNull Long roomId,
                                  @Valid @RequestBody(required = false) FriendDeleteReq request) {
        Long uid = RequestHolder.get().getUid();
        friendService.deleteFriend(uid, roomId,
                request != null && Boolean.TRUE.equals(request.getClearHistory()));
        return ApiResult.success();
    }

    @PostMapping("/black/{roomId}")
    @Operation(summary = "拉黑好友")
    public ApiResult<Void> black(@PathVariable("roomId") @Positive @NotNull Long roomId) {
        Long uid = RequestHolder.get().getUid();
        friendService.blackFriend(uid, roomId);
        return ApiResult.success();
    }

    @PostMapping("/unBlack/{roomId}")
    @Operation(summary = "解除好友黑名单")
    public ApiResult<Void> unBlack(@PathVariable("roomId") @Positive Long roomId) {
        Long uid = RequestHolder.get().getUid();
        friendService.unBlackFriend(uid, roomId);
        return ApiResult.success();
    }

    @PostMapping("/page")
    @Operation(summary = "好友联系人列表")
    public ApiResult<CursorPageBaseResp<FriendResp>> friendList(@Valid @RequestBody FriendPageReq request) {
        return ApiResult.success(friendService.friendList(RequestHolder.get().getUid(), request));
    }

    @PostMapping("/topStatus")
    @Operation(summary = "设置好友或群置顶")
    public ApiResult<Void> setFriendTop(@Valid @RequestBody TopOrUnTopReq request) {
        contactService.changeTopStatus(RequestHolder.get().getUid(),request);
        return ApiResult.success();
    }

    @PostMapping("/info")
    @Operation(summary = "查看好友详细资料")
    public ApiResult<FriendDetailResp> friendDetail(@Valid @RequestBody FriendDetailReq request) {
        return ApiResult.success(friendService.userMemberDetail(
                RequestHolder.get().getUid(), request.getTargetUid(), request.getRoomId()));
    }

}
