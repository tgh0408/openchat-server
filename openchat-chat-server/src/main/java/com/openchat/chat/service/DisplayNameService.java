package com.openchat.chat.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.entity.UserFriend;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.user.domain.vo.resp.UserInfoResp;
import com.openchat.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 名称按阅读者及房间解析；私有备注不进入群共享消息或共享用户缓存。 */
@Service
@RequiredArgsConstructor
public class DisplayNameService {
    private final GroupMemberDao members;
    private final RoomDao rooms;
    private final UserDao users;
    private final RoomAccessService access;

    /** 资料接口在房间授权后补充名称，身份只能由 Controller 从 RequestHolder 传入。 */
    public UserInfoResp fillProfile(UserInfoResp response, Long viewerUid, Long roomId) {
        Room room = rooms.get(roomId);
        requireProfileAccess(viewerUid, response.getUid(), roomId);
        response.setRoomId(roomId);
        response.setGroupNickname(groupNickname(room, response.getUid()));
        response.setRemark(remark(viewerUid, response.getUid()));
        return response;
    }

    /** 房间资料必须同时校验阅读者和目标用户，避免借公开资料接口越过房间成员边界。 */
    private void requireProfileAccess(Long viewerUid, Long targetUid, Long roomId) {
        if (roomId == null) return;
        Room accessibleRoom = access.requireCurrentMember(viewerUid, roomId);
        switch (accessibleRoom.getType()) {
            case GROUP -> {
                if (members.getMember(roomId, targetUid) == null) {
                    throw new BusinessException("目标用户不属于当前群聊");
                }
            }
            case FRIEND -> {
                if (!Objects.equals(access.requireFriendPeer(viewerUid, roomId), targetUid)) {
                    throw new BusinessException("目标用户不属于当前单聊房间");
                }
            }
            case FILE_ASSISTANT -> {
                if (!Objects.equals(viewerUid, targetUid)) {
                    throw new BusinessException("目标用户不属于当前文件助手");
                }
            }
            default -> throw new BusinessException("当前房间不支持查询用户资料");
        }
    }

    /** 共享 SSE 只读取本群公开昵称，不读取任何接收者的好友备注。 */
    public String groupNickname(Long roomId, Long uid) {
        return groupNickname(rooms.get(roomId), uid);
    }

    private String groupNickname(Room room, Long uid) {
        if (room == null || room.getType() != RoomTypeEnum.GROUP) return null;
        var member = members.getMember(room.getId(), uid);
        return member == null ? null : nonBlank(member.getNickname());
    }

    /** 备注直接按阅读者查有效好友关系，不新增难以失效的私有共享缓存。 */
    private String remark(Long viewerUid, Long uid) {
        if (viewerUid == null || Objects.equals(viewerUid, uid)) return null;
        UserFriend friend = access.findFriendRelation(viewerUid, uid);
        return friend == null ? null : nonBlank(friend.getNickname());
    }

    public String resolve(Long viewerUid, Long roomId, Long uid) {
        String name = remark(viewerUid, uid);
        if (name != null) return name;
        name = groupNickname(roomId, uid);
        if (name != null) return name;
        var user = users.get(IdTypeEnum.ID, uid);
        name = user == null ? null : nonBlank(user.getName());
        return name == null ? "用户 " + uid : name;
    }

    /** 单聊会话标题没有群昵称，优先使用当前阅读者备注。 */
    public String friendName(Long viewerUid, Long uid, String username) {
        String name = remark(viewerUid, uid);
        if (name != null) return name;
        name = nonBlank(username);
        return name == null ? "用户 " + uid : name;
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
