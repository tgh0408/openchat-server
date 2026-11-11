package com.openchat.user.service.adapter;

import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.vo.resp.GroupMemberInfo;

import java.util.List;
import java.util.Map;

public class GroupMemberAdapter {

    public static List<GroupMemberInfo> getGroupMembers(List<GroupMember> memberUidList, Map<Long, User> infoCacheBatch) {
        return memberUidList.stream().map(member -> {
            GroupMemberInfo groupMemberInfo = new GroupMemberInfo();
            groupMemberInfo.setRole(member.getRole() == null ? null : member.getRole().getRole());
            groupMemberInfo.setUid(member.getUid());
            User user = infoCacheBatch.get(member.getUid());
            groupMemberInfo.setUsername(user == null ? "" : user.getName());
            groupMemberInfo.setNickname(member.getNickname());
            return groupMemberInfo;
        }).toList();
    }
}
