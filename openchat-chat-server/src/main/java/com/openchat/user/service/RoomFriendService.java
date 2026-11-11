package com.openchat.user.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.service.adapter.ChatAdapter;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.user.domain.entity.RoomFriend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** 复用双方唯一的单聊映射，并补齐缺失会话，不修改好友关系。 */
@Service
@RequiredArgsConstructor
public class RoomFriendService {
    public static final String SPLIT = "_";
    final RoomFriendDao roomFriendDao;
    final RoomDao roomDao;
    final ContactDao contactDao;

    @Transactional(rollbackFor = Exception.class)
    public RoomFriend createFriendRoom(List<Long> list) {
        if (list.size() != 2) {
            throw new BusinessException("好友数量不对，创建房间失败");
        }
        String key = list.stream().sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(SPLIT));
        RoomFriend roomFriend = roomFriendDao.get(key);
        if (roomFriend == null) {
            Room room = createRoom(RoomTypeEnum.FRIEND);
            //创建单人聊天房间，主要放置我和好友的id
            roomFriend = createFriendRoom(room.getId(), list, key);
        }
        contactDao.createMissingContacts(roomFriend.getRoomId(), list);
        return roomFriend;
    }

    private RoomFriend createFriendRoom(Long roomId, List<Long> uidList, String key) {
        List<Long> collect = uidList.stream().sorted().toList();
        RoomFriend roomFriend = new RoomFriend();
        roomFriend.setRoomId(roomId);
        roomFriend.setUid1(collect.get(0));
        roomFriend.setUid2(collect.get(1));
        roomFriend.setRoomKey(key);
        roomFriendDao.save(roomFriend);
        return roomFriend;
    }

    private Room createRoom(RoomTypeEnum roomTypeEnum) {
        Room room = ChatAdapter.buildRoom(roomTypeEnum);
        roomDao.save(room);
        return room;
    }

}
