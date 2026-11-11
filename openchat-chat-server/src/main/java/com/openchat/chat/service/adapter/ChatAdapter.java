package com.openchat.chat.service.adapter;

import com.openchat.chat.domain.entity.Contact;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.enums.HotFlagEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.user.domain.entity.RoomFriend;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Description:
 * Author: 
 * Date: 2023-07-22
 */
public class ChatAdapter {
    public static final String SEPARATOR = ",";

    public static String generateRoomKey(List<Long> uidList) {
        return uidList.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(SEPARATOR));
    }

    public static Room buildRoom(RoomTypeEnum typeEnum) {
        Room room = new Room();
        room.setType(typeEnum);
        room.setHotFlag(HotFlagEnum.NOT);
        return room;
    }


    public static Contact buildContact(Long uid, Long roomId) {
        Contact contact = new Contact();
        contact.setRoomId(roomId);
        contact.setUid(uid);
        return contact;
    }

    public static Set<Long> getFriendUidSet(Collection<RoomFriend> values, Long uid) {
        return values.stream()
                .map(a -> getFriendUid(a, uid))
                .collect(Collectors.toSet());
    }

    /**
     * 获取好友uid
     */
    public static Long getFriendUid(RoomFriend roomFriend, Long uid) {
        return Objects.equals(uid, roomFriend.getUid1()) ? roomFriend.getUid2() : roomFriend.getUid1();
    }

    /** 空名称表示尚未显式命名，默认群名由前端按前九位成员名称拼接。 */
    public static RoomGroup buildGroupRoom(Long roomId) {
        RoomGroup roomGroup = new RoomGroup();
        roomGroup.setName("");
        roomGroup.setRoomId(roomId);
        return roomGroup;
    }
}
