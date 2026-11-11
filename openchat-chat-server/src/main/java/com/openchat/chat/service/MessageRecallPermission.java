package com.openchat.chat.service;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.enums.GroupRoleEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.user.dao.UserRoleDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 展示与预检复用成员缓存；最终授权当前读，不能被缓存中的旧管理员权限放行。 */
@Service
@RequiredArgsConstructor
public class MessageRecallPermission {
    private final GroupMemberDao members;

    public boolean canManage(Long uid, Long roomId, RoomTypeEnum type) {
        if (type != RoomTypeEnum.GROUP) return false;
        GroupMember member = members.getMember(roomId, uid);
        if (member == null) return false;
        return GroupRoleEnum.isAdmin(member.getRole());
    }
}
