package com.openchat.chat.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.dto.RoomBaseInfo;
import com.openchat.chat.domain.entity.*;
import com.openchat.chat.domain.enums.GroupRoleEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.vo.req.AddContactReq;
import com.openchat.chat.domain.vo.req.DeleteContactReq;
import com.openchat.chat.domain.vo.resp.ChatRoomResp;
import com.openchat.chat.service.abstracts.AbstractMsgHandler;
import com.openchat.chat.service.abstracts.MsgHandlerFactory;
import com.openchat.chat.service.adapter.ChatAdapter;
import com.openchat.chat.dao.RoomGroupDao;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.user.dao.UserDao;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.domain.entity.RoomFriend;
import com.openchat.user.domain.entity.User;
import com.openchat.user.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.openchat.chat.domain.enums.MessageTypeEnum.*;

@Service
@RequiredArgsConstructor
public class RoomAppService {
    final UserDao userDao;
    final RoomGroupDao roomGroupDao;
    final RoomFriendDao roomFriendDao;
    final GroupMemberDao groupMemberDao;
    final DisplayNameService displayNames;

    public Map<Long, RoomBaseInfo> getRoomBaseInfoMap(List<Room> rooms, Long uid) {
        Map<Long, Room> roomMap = rooms.stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        // 房间根据好友和群组类型分组
        Map<RoomTypeEnum, List<Long>> groupRoomIdMap = roomMap.values().stream().collect(Collectors.groupingBy(Room::getType,
                Collectors.mapping(Room::getId, Collectors.toList())));
        // 获取群组信息
        List<Long> groupRoomId = groupRoomIdMap.get(RoomTypeEnum.GROUP);
        Map<Long, RoomGroup> roomInfoBatch = roomGroupDao.getBatch(groupRoomId);
        Map<Long, List<GroupMember>> groupMemberBatch = CollectionUtil.isEmpty(groupRoomId)
                ? Map.of()
                : groupMemberDao.getBatch(groupRoomId);
        // 获取好友信息
        List<Long> friendRoomId = groupRoomIdMap.get(RoomTypeEnum.FRIEND);
        Map<Long, User> friendRoomMap = getFriendRoomMap(friendRoomId, uid);

        return roomMap.values().stream().map(room -> {
            RoomBaseInfo roomBaseInfo = new RoomBaseInfo();
            roomBaseInfo.setRoomId(room.getId());
            roomBaseInfo.setType(room.getType());
            roomBaseInfo.setHotFlag(room.getHotFlag());
            roomBaseInfo.setLastMsgId(room.getLastMsgId());
            roomBaseInfo.setActiveTime(room.getActiveTime());
            if (room.getType() == RoomTypeEnum.GROUP) {
                RoomGroup roomGroup = roomInfoBatch.get(room.getId());
                roomBaseInfo.setName(roomGroup.getName());
                roomBaseInfo.setAvatarUidList(groupMemberBatch
                        .getOrDefault(room.getId(), List.of())
                        .stream()
                        .sorted(FriendService.groupMemberComparator())
                        .limit(9)
                        .map(GroupMember::getUid)
                        .toList());
            } else if (room.getType() == RoomTypeEnum.FRIEND) {
                User user = friendRoomMap.get(room.getId());
                roomBaseInfo.setParticipantId(user.getId());
                roomBaseInfo.setName(displayNames.friendName(uid, user.getId(), user.getName()));
            } else if (room.getType() == RoomTypeEnum.FILE_ASSISTANT) {
                roomBaseInfo.setName(FileAssistantService.NAME);
                // 客户端按 type=3 展示内置文件助手图标，不使用用户头像或虚构的 OSS 地址。
            }
            return roomBaseInfo;
        }).collect(Collectors.toMap(RoomBaseInfo::getRoomId, Function.identity()));
    }

    public Map<Long, User> getFriendRoomMap(List<Long> roomIds, Long uid) {
        if (CollectionUtil.isEmpty(roomIds)) {
            return new HashMap<>();
        }
        Map<Long, RoomFriend> roomFriendMap = roomFriendDao.getBatch(roomIds);
        // 对方身份只从当前阅读者所属的单聊关系推导，不能把房间号当作用户 ID。
        for (RoomFriend roomFriend : roomFriendMap.values()) {
            if (!Objects.equals(uid, roomFriend.getUid1()) && !Objects.equals(uid, roomFriend.getUid2())) {
                throw new BusinessException("房间号与id不匹配");
            }
        }
        Set<Long> friendUidSet = ChatAdapter.getFriendUidSet(roomFriendMap.values(), uid);
        Map<Long, User> userBatch = userDao.getBatch(new ArrayList<>(friendUidSet));
        return roomFriendMap.values()
                .stream()
                .collect(Collectors.toMap(RoomFriend::getRoomId, roomFriend -> {
                    Long friendUid = ChatAdapter.getFriendUid(roomFriend, uid);
                    return userBatch.get(friendUid);
                }));
    }
}
