package com.openchat.user.service.adapter;

import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.entity.RoomGroupExtra;
import com.openchat.common.domain.vo.resp.GroupResp;
import lombok.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GroupAdapter {
    public static List<GroupResp> init(Collection<RoomGroup> roomGroups , Map<Long, GroupMember> groupMembersMapper, Long uid) {
        // room 可能会比 group 少
        return roomGroups.stream()
                .map(roomGroup -> {
                    GroupMember groupMember = groupMembersMapper.get(roomGroup.getRoomId());
                    if (groupMember == null) {
                        return new GroupResp();
                    }
                    return getGroupResp(roomGroup, groupMember,uid);
                }).toList();
    }

    public static GroupResp init(RoomGroup roomGroup,GroupMember groupMember,Long uid){
        return getGroupResp(roomGroup, groupMember,uid);
    }

    private static @NonNull GroupResp getGroupResp(RoomGroup roomGroup, GroupMember groupMember,Long uid) {
        GroupResp groupResp = new GroupResp();
        groupResp.setRoot(groupMember.getRole() == com.openchat.chat.domain.enums.GroupRoleEnum.SUPER_ADMIN
                || groupMember.getRole() == com.openchat.chat.domain.enums.GroupRoleEnum.ADMIN);
        groupResp.setName(roomGroup.getName());
        groupResp.setGroupId(roomGroup.getUid());
        if (roomGroup.getExtJson() == null) {
            groupResp.setNotice(null);
            groupResp.setQrCode(List.of());
        }else {
            RoomGroupExtra extra = roomGroup.getExtJson();
            RoomGroupExtra.Notice notice = extra.getNotice();
            groupResp.setNotice(notice == null ? null : new GroupResp.Notice(
                    notice.getDescription(), notice.getCreateTime(), notice.getUid()));
            List<RoomGroupExtra.QRCode> qrCodes =
                    extra.getQrCode() == null ? List.of() : extra.getQrCode();
            groupResp.setQrCode(qrCodes.stream()
                    .map(qrCode -> new GroupResp.QRCode(
                            qrCode.getUrl(),
                            qrCode.getCreateTime(),
                            qrCode.getExpireTime(),
                            qrCode.getKeyword()))
                    .toList());
        }
        groupResp.setRoomId(roomGroup.getRoomId());
        groupResp.setCreate(Objects.equals(roomGroup.getUid(), uid));
        return groupResp;
    }
}
