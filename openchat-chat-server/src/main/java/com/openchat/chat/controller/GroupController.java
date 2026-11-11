package com.openchat.chat.controller;

import com.openchat.chat.domain.vo.req.CreateGroupReq;
import com.openchat.chat.domain.vo.req.AddGroupMembersReq;
import com.openchat.chat.domain.vo.req.RemoveGroupMembersReq;
import com.openchat.chat.domain.vo.req.UpdateGroupInfoReq;
import com.openchat.chat.service.RoomGroupService;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.domain.vo.resp.GroupResp;
import com.openchat.common.utils.RequestHolder;
import com.openchat.user.domain.vo.resp.GroupMemberInfo;
import com.openchat.user.service.FriendService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/capi/group")
@Tag(name = "群相关接口")
@RequiredArgsConstructor
public class GroupController {
    final RoomGroupService roomGroupService;
    final FriendService friendService;

    @PostMapping("/members/remove")
    @Operation(summary = "群主或管理员移出群成员")
    public ApiResult<Void> removeMembers(@Valid @RequestBody RemoveGroupMembersReq request) {
        roomGroupService.removeMembers(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PostMapping("/members/add")
    @Operation(summary = "将好友直接添加到已有群聊")
    public ApiResult<Void> addMembers(@Valid @RequestBody AddGroupMembersReq request) {
        roomGroupService.addMembers(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PostMapping("/create")
    @Operation(summary = "创建群聊并添加所选好友")
    public ApiResult<GroupResp> create(@Valid @RequestBody CreateGroupReq request) {
        return ApiResult.success(roomGroupService.createGroup(request, RequestHolder.get().getUid()));
    }


    @PostMapping("/groupInfo/{id}")
    @Operation(summary = "获取群信息")
    public ApiResult<GroupResp> groupInfo(@NotNull @PathVariable("id") Long roomId){
        return ApiResult.success(roomGroupService.getGroupInfo(roomId,RequestHolder.get().getUid()));
    }

    @PostMapping("/page/group")
    @Operation(summary = "群列表")
    public ApiResult<List<GroupResp>> friendList(){
        return ApiResult.success(friendService.groupList(RequestHolder.get().getUid()));
    }

    @PostMapping("/page/groupMember/{id}")
    @Operation(summary = "获取群好友列表")
    public ApiResult<List<GroupMemberInfo>> friendListMember(@PathVariable("id") Long roomId){
        return ApiResult.success(roomGroupService.getGroupMembers(RequestHolder.get().getUid(),roomId));
    }


    @PostMapping("/delGroup/{id}")
    @Operation(summary = "解散群组")
    public ApiResult<Void> delGroup(@NotNull @PathVariable("id") Long roomId){
        roomGroupService.delGroup(RequestHolder.get().getUid(),roomId);
        return ApiResult.success();
    }

    @PostMapping("/exitGroup/{roomId}")
    @Operation(summary = "退出群聊")
    public ApiResult<Void> exitGroup(@NotNull @PathVariable Long roomId){
        roomGroupService.exitGroup(RequestHolder.get().getUid(),roomId);
        return ApiResult.success();
    }

    @PostMapping("/update")
    @Operation(summary = "修改群名称、群公告")
    public ApiResult<GroupResp> update(@Valid @RequestBody UpdateGroupInfoReq request) {
        return ApiResult.success(
                roomGroupService.updateGroupInfo(request, RequestHolder.get().getUid()));
    }
}
