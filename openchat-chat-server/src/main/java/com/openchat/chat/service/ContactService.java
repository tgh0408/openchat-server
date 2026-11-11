package com.openchat.chat.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Pair;
import com.openchat.chat.dao.*;
import com.openchat.chat.domain.dto.RoomBaseInfo;
import com.openchat.chat.domain.entity.*;
import com.openchat.chat.domain.enums.ContactStateEnum;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.AddContactReq;
import com.openchat.chat.domain.vo.req.DeleteContactReq;
import com.openchat.chat.domain.vo.req.HideContactReq;
import com.openchat.chat.domain.vo.req.ContactNotDisturbReq;
import com.openchat.chat.domain.vo.resp.ChatRoomResp;
import com.openchat.chat.service.abstracts.AbstractMsgHandler;
import com.openchat.chat.service.abstracts.MsgHandlerFactory;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.vo.req.TopOrUnTopReq;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.openchat.chat.domain.enums.MessageTypeEnum.*;

@Service
@RequiredArgsConstructor
public class ContactService {
    final ContactDao contactDao;
    final RoomDao roomDao;
    final RoomAccessService roomAccessService;
    final MessageDao messageDao;
    final RoomAppService roomAppService;
    final RoomGroupDao roomGroupDao;
    final GroupMemberDao groupMemberDao;
    final MessageRecallPermission recallPermission;
    final DisplayNameService displayNames;
    final PlatformTransactionManager transactionManager;

    /** 只修改本人已有会话偏好，退群或删除好友后仍可关闭本人的提醒。 */
    public void changeNotDisturb(Long uid, ContactNotDisturbReq request) {
        if (uid == null || uid <= 0 || request == null || request.getRoomId() == null
                || request.getRoomId() <= 0 || request.getStatus() == null) {
            throw new BusinessException("无效的会话免打扰参数");
        }
        contactDao.changeNotDisturb(uid, request.getRoomId(), request.getStatus());
    }

    public void changeTopStatus(Long uid, @Valid TopOrUnTopReq request) {
        long roomId = request.getRoomId();
        Room room = roomAccessService.requireCurrentMember(uid, roomId);
        TopOrUnTopReq resolved = new TopOrUnTopReq();
        resolved.setRoomId(room.getId());
        resolved.setStatus(request.getStatus());
        boolean updated = contactDao.changeTopStatus(uid, resolved);
        if (updated) return;
        Contact contact = contactDao.getByRoomIdAndUid(roomId, uid);
        if (contact == null) {
            throw new BusinessException("会话不存在或已被移除");
        }
        if (contact.getIsTop() != resolved.getStatus()) {
            throw new BusinessException("会话置顶状态更新失败，请重试");
        }
    }

    public List<ChatRoomResp> getContactList(Long uid) {
        List<Contact> contacts = contactDao.getAllContactList(uid);
        if (contacts.isEmpty()) return List.of();
        List<Long> roomIds = contacts.stream().map(Contact::getRoomId).toList();
        Map<Long, Room> rooms = roomDao.getBatch(roomIds);
        List<Contact> visibleContacts = new ArrayList<>();
        boolean hasInactiveContacts = contacts.stream().anyMatch(this::isInactiveContact);
        if (hasInactiveContacts) {
            // 只把隐藏/删除会话的状态协调放入短事务，避免列表组装阶段绕过共享缓存。
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> resolveContacts(uid, contacts, rooms, visibleContacts));
        } else {
            resolveContacts(uid, contacts, rooms, visibleContacts);
        }
        if (visibleContacts.isEmpty()) return List.of();
        List<Long> visibleRoomIds = visibleContacts.stream().map(Contact::getRoomId).toList();
        List<Room> visibleRooms = visibleRoomIds.stream()
                .map(rooms::get)
                .filter(Objects::nonNull)
                .toList();
        return buildContactResp(uid, visibleContacts, new ArrayList<>(visibleRooms));
    }

    private void resolveContacts(Long uid, List<Contact> contacts, Map<Long, Room> rooms,
                                 List<Contact> visibleContacts) {
        for (Contact contact : contacts) {
            Room room = rooms.get(contact.getRoomId());
            Room refreshedRoom = refreshInactiveRoomSnapshot(contact, room);
            if (refreshedRoom != room && refreshedRoom != null) {
                rooms.put(contact.getRoomId(), refreshedRoom);
                room = refreshedRoom;
            }
            Contact restored = restoreContactIfNeeded(uid, contact, room);
            if (restored != null && restored.getContactState() == ContactStateEnum.NORMAL) {
                visibleContacts.add(restored);
            }
        }
    }

    private boolean isInactiveContact(Contact contact) {
        return contact != null
                && (contact.getContactState() == ContactStateEnum.DELETED
                || contact.getContactState() == ContactStateEnum.HIDDEN);
    }

    /**
     * 已删除会话的房间活跃状态可能仍在异步写后窗口内；用消息 DAO 的最新记录补齐本次列表快照。
     */
    private Room refreshInactiveRoomSnapshot(Contact contact, Room room) {
        if (contact == null
                || (contact.getContactState() != ContactStateEnum.DELETED
                && contact.getContactState() != ContactStateEnum.HIDDEN)
                || room == null || room.getType() == null) {
            return room;
        }
        Message latest = messageDao.getLastMsgId(room.getType(), room.getId());
        if (latest == null || latest.getId() == null
                || latest.getId() <= Optional.ofNullable(room.getLastMsgId()).orElse(0L)) {
            return room;
        }
        Room refreshed = new Room();
        BeanUtil.copyProperties(room, refreshed);
        refreshed.setLastMsgId(latest.getId());
        refreshed.setActiveTime(latest.getCreateTime());
        return refreshed;
    }

    /** 刷新隐藏会话；并发冲突时重新读取状态，避免用旧版本强行恢复。 */
    private Contact restoreContactIfNeeded(Long uid, Contact contact, Room room) {
        if (contact == null
                || (contact.getContactState() != ContactStateEnum.DELETED
                && contact.getContactState() != ContactStateEnum.HIDDEN)
                || room == null
                || room.getLastMsgId() == null || room.getLastMsgId() <= 0) {
            return contact;
        }
        if (room.getType() == RoomTypeEnum.GROUP
                && groupMemberDao.getMember(room.getId(), uid) == null) {
            return contact;
        }
        long boundary = getContactMessageBoundary(contact);
        if (room.getLastMsgId() <= boundary) return contact;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (contactDao.restoreIfNewMessage(contact, room.getLastMsgId())) return contact;
            Contact latest = contactDao.getContactStatus(room.getId(), uid);
            if (latest == null || latest.getContactState() == ContactStateEnum.NORMAL) return latest;
            if (latest.getContactState() != ContactStateEnum.DELETED
                    && latest.getContactState() != ContactStateEnum.HIDDEN) {
                return latest;
            }
            contact = latest;
            if (latest.getVersion() == null) return latest;
            boundary = getContactMessageBoundary(latest);
            if (room.getLastMsgId() <= boundary) return latest;
        }
        return contact;
    }

    /**
     * Returns the message boundary that must be skipped when calculating a contact's unread messages.
     */
    private long getContactMessageBoundary(Contact contact) {
        return Math.max(
                Optional.ofNullable(contact.getLastDeleteId()).orElse(0L),
                Optional.ofNullable(contact.getLastHiddenMsgId()).orElse(0L));
    }

    public void addContact(AddContactReq request, Long uid) {
        Room accessibleRoom = roomAccessService.requireCurrentMember(uid, request.getRoomId());
        if (accessibleRoom.getType() == RoomTypeEnum.FILE_ASSISTANT) {
            // 文件助手不依赖好友关系，恢复会话时保留已读、置顶和历史删除边界。
            contactDao.createMissingContacts(accessibleRoom.getId(), List.of(uid));
            contactDao.changeContactStatus(accessibleRoom.getId(), uid);
            return;
        }
        //查询当前的会话是否存在
        Contact contactActive = contactDao.getContactStatus(request.getRoomId(), uid);
        if (Objects.nonNull(contactActive) && contactActive.getContactState() == ContactStateEnum.NORMAL) {
            return;
        }
        Room room = roomDao.get(request.getRoomId());
        if (room == null) {
            throw new BusinessException("此房间不存在");
        }
        RoomTypeEnum roomType = room.getType();
        // 按房间类型恢复会话，不用单聊映射推断好友关系。
        switch (roomType) {
            case FRIEND:
                addFriendContact(request, uid, contactActive);
                break;
            case GROUP:
                addGroupContact(request, uid);
                break;
            default:
                throw new BusinessException("无效的房间类型");
        }
    }

    private void addFriendContact(AddContactReq request, Long uid, Contact contactActive) {
        roomAccessService.requireFriendPeer(uid, request.getRoomId());
        if (contactActive != null && contactActive.getContactState() != ContactStateEnum.NORMAL) {
            contactDao.changeContactStatus(request.getRoomId(), uid);
        } else {
            Contact contact = new Contact();
            contact.setRoomId(request.getRoomId());
            contact.setUid(uid);
            //此房间可能被删了记录存在
            contactDao.save(contact);
        }
    }

    private void addGroupContact(AddContactReq request, Long uid) {
        GroupMember groupMember = groupMemberDao.getMember(request.getRoomId(),uid);
        if (Objects.isNull(groupMember)) {
            throw new BusinessException("此群不存在或者你不在这个群中");
        }
        Contact contact = new Contact();
        contact.setRoomId(request.getRoomId());
        contact.setUid(uid);
        contactDao.saveOrRestore(contact);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteContact(DeleteContactReq request, Long uid) {
        if (uid == null || uid <= 0 || request == null
                || request.getRoomId() == null || request.getRoomId() <= 0) {
            throw new BusinessException("无效的会话参数");
        }
        Long roomId = request.getRoomId();
        Room accessibleRoom = roomAccessService.requireRoom(roomId);
        RoomTypeEnum roomType = accessibleRoom.getType();
        Contact contactActive = contactDao.getContactStatus(roomId, uid);
        if (Objects.isNull(contactActive)) {
            // 没有本人的会话记录时，仍需校验当前权限后才能补建。
            roomAccessService.requireCurrentMember(uid, roomId);
            contactDao.createMissingContacts(roomId, List.of(uid));
        }
        boolean group = roomType == RoomTypeEnum.GROUP;
        Message latest = messageDao.getLastMsgId(roomType, roomId);
        long latestId = latest == null ? 0L : latest.getId();
        long boundary = request.getLastDeleteId() == null ? latestId : request.getLastDeleteId();
        if (boundary < 0 || boundary > latestId) {
            throw new BusinessException("删除消息边界无效");
        }
        if (request.getLastDeleteId() == null && contactActive != null) {
            boundary = Math.max(boundary, Optional.ofNullable(contactActive.getLastDeleteId()).orElse(0L));
        }
        contactDao.clearHistory(uid, roomId, boundary, group);
    }

    @Transactional(rollbackFor = Exception.class)
    public void hideContact(HideContactReq request, Long uid) {
        if (uid == null || uid <= 0 || request == null
                || request.getRoomId() == null || request.getRoomId() <= 0) {
            throw new BusinessException("无效的会话参数");
        }
        Room room = roomAccessService.requireCurrentMember(uid, request.getRoomId());
        Message latest = messageDao.getLastMsgId(room.getType(), room.getId());
        long latestMsgId = latest == null ? 0L : latest.getId();
        contactDao.hideContact(room.getId(), uid, latestMsgId);
    }

    public ChatRoomResp getContactInfo(Long uid, Long roomId) {
        if (uid == null || uid <= 0 || roomId == null || roomId <= 0) {
            throw new BusinessException("无效的会话参数");
        }
        Room room = roomAccessService.requireRoom(roomId);
        Contact contact = contactDao.getContactStatus(roomId, uid);
        if (room.getType() == RoomTypeEnum.GROUP) {
            RoomGroup group = roomGroupDao.get(roomId);
            GroupMember member = groupMemberDao.getMember(roomId, uid);
            if (member == null || group == null) {
                if (contact == null) throw new BusinessException("会话不存在");
                return departedContact(contact);
            }
        } else {
            // 非群会话仍按当前有效关系返回消息摘要。
            roomAccessService.requireCurrentMember(uid, roomId);
        }
        //校验
        if (Objects.isNull(contact) || contact.getContactState() != ContactStateEnum.NORMAL) {
            contact = restoreContactInTransaction(uid, roomId);
        }
        if (Objects.isNull(contact) || contact.getContactState() != ContactStateEnum.NORMAL) {
            throw new BusinessException("会话不存在");
        }
        // 详情只返回快照，实际阅读由独立确认接口推进，避免后台刷新误标已读。
        List<ChatRoomResp> result = buildContactResp(uid, List.of(contact), List.of(room));
        result.getFirst().setCanRecallMembers(
                recallPermission.canManage(uid, roomId, room.getType()));
        return result.getFirst();
    }

    /**
     * 仅将会话补建/恢复及其最终状态读取放入事务，详情响应组装保持在事务外以复用共享缓存。
     */
    private Contact restoreContactInTransaction(Long uid, Long roomId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            AddContactReq addContactReq = new AddContactReq();
            addContactReq.setRoomId(roomId);
            addContact(addContactReq, uid);
            return contactDao.getContactStatus(roomId, uid);
        });
    }

    /** 离群后只返回本人 contact，不查询群消息；名称、头像和摘要由客户端保留本地值。 */
    private ChatRoomResp departedContact(Contact contact) {
        return ChatRoomResp.builder()
                .roomId(contact.getRoomId()).type(RoomTypeEnum.GROUP)
                .isTop(Optional.ofNullable(contact.getIsTop()).orElse(YesOrNoEnum.NO))
                .notDisturb(Optional.ofNullable(contact.getNotDisturb()).orElse(YesOrNoEnum.NO))
                .name("")
                .avatarUidList(List.of())
                .activeTime(Optional.ofNullable(contact.getActiveTime())
                        .orElseGet(() -> Optional.ofNullable(contact.getCreateTime()).orElse(new Date(0))))
                .unreadCount(0).lastMsgId(0L)
                .lastReadMsgId(Math.max(Optional.ofNullable(contact.getLastReadMsgId()).orElse(0L),
                        Optional.ofNullable(contact.getLastDeleteId()).orElse(0L)))
                .groupRemoved(true)
                .canRecallMembers(false)
                .build();
    }

    @NotNull
    private List<ChatRoomResp> buildContactResp(Long uid, List<Contact> contactList, List<Room> rooms) {
        //是否置顶
        Map<Long, YesOrNoEnum> is_top = contactList.stream().collect(Collectors.toMap(
                Contact::getRoomId,
                Contact::getIsTop));
        Map<Long, YesOrNoEnum> notDisturb = contactList.stream().collect(Collectors.toMap(
                Contact::getRoomId,
                contact -> Optional.ofNullable(contact.getNotDisturb()).orElse(YesOrNoEnum.NO)));
        // 表情和头像
        Map<Long, RoomBaseInfo> roomBaseInfoMap = roomAppService.getRoomBaseInfoMap(rooms, uid);
        // 最后一条消息
        // 区分单聊群聊
        List<Long> groupMsgIds = roomBaseInfoMap.values().stream()
                .filter(item -> item.getType() == RoomTypeEnum.GROUP)
                .map(RoomBaseInfo::getLastMsgId)
                .filter(id -> id != null && id > 0)
                .toList();
        List<Message> messages = CollectionUtil.isEmpty(groupMsgIds)
                ? new ArrayList<>()
                : messageDao.listByIds(RoomTypeEnum.GROUP, groupMsgIds);
        //单聊的消息id
        List<Long> friendMsgIds = roomBaseInfoMap.values().stream()
                .filter(item -> item.getType().usesUserMessageTable())
                .map(RoomBaseInfo::getLastMsgId)
                .filter(id -> id != null && id > 0)
                .toList();
        List<Message> friendMessages = CollectionUtil.isEmpty(friendMsgIds)
                ? new ArrayList<>()
                : messageDao.listByIds(RoomTypeEnum.FRIEND, friendMsgIds);
        //消息整合
        messages.addAll(friendMessages);

        Map<Long, Message> msgMap = messages.stream().collect(Collectors.toMap(Message::getId, Function.identity()));
        // 消息未读数
        Map<Long, Integer> unReadCountMap = getUnReadCountMap(uid, contactList, roomBaseInfoMap);
        Map<Long, Long> deletedIds = contactList.stream().collect(Collectors.toMap(
                Contact::getRoomId, contact -> Optional.ofNullable(contact.getLastDeleteId()).orElse(0L)));
        Map<Long, Long> readIds = contactList.stream().collect(Collectors.toMap(
                Contact::getRoomId, contact -> Math.max(
                        Optional.ofNullable(contact.getLastReadMsgId()).orElse(0L),
                        getContactMessageBoundary(contact))));
        return roomBaseInfoMap.values().stream().map(room -> {
                    ChatRoomResp resp = new ChatRoomResp();
                    RoomBaseInfo roomBaseInfo = roomBaseInfoMap.get(room.getRoomId());
                    resp.setAvatarUidList(roomBaseInfo.getAvatarUidList());
                    resp.setRoomId(room.getRoomId());
                    resp.setLastMsgId(Optional.ofNullable(room.getLastMsgId()).orElse(0L));
                    resp.setLastReadMsgId(readIds.getOrDefault(room.getRoomId(), 0L));
                    resp.setParticipantId(roomBaseInfo.getParticipantId());
                    resp.setActiveTime(room.getActiveTime());
                    resp.setIsTop(is_top.get(room.getRoomId()));
                    resp.setNotDisturb(notDisturb.get(room.getRoomId()));
                    resp.setType(roomBaseInfo.getType());
                    resp.setName(roomBaseInfo.getName());
                    Message message = msgMap.get(room.getLastMsgId());
                    if (Objects.nonNull(message) && message.getId() > deletedIds.getOrDefault(room.getRoomId(), 0L)) {
                        AbstractMsgHandler<?> strategyNoNull = MsgHandlerFactory.getStrategyNoNull(message.getType());
                        if (MessageTypeEnum.equalsIn(message.getType(),RECALL,SYSTEM,RTC_CALL)) {
                            // 撤回者可能是管理员，不能继续添加原消息发送者前缀。
                            resp.setText(strategyNoNull.showContactMsg(message, uid));
                        } else {
                            resp.setText(displayNames.resolve(uid, room.getRoomId(), message.getFromUid()) + ":" + strategyNoNull.showContactMsg(message));
                        }
                    }
                    resp.setUnreadCount(unReadCountMap.getOrDefault(room.getRoomId(), 0));
                    return resp;
                }).sorted(Comparator.comparingInt((ChatRoomResp resp) ->
                                Optional.ofNullable(resp.getIsTop().getStatus()).orElse(YesOrNoEnum.NO.getStatus()))
                        .reversed()
                        .thenComparing(ChatRoomResp::getActiveTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ChatRoomResp::getRoomId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 获取未读数
     */
    private Map<Long, Integer> getUnReadCountMap(Long uid, List<Contact> contacts,
                                                 Map<Long, RoomBaseInfo> rooms) {
        if (Objects.isNull(uid) || contacts.isEmpty()) {
            return new HashMap<>();
        }
        // 单次查库核对本人有效群关系，不使用可能滞后的成员缓存，也不从列表移除离群会话。
        boolean hasGroups = rooms.values().stream()
                .anyMatch(room -> room.getType() == RoomTypeEnum.GROUP);
        Set<Long> joinedGroupIds = hasGroups
                ? groupMemberDao.getUserRoom(uid).stream()
                .map(GroupMember::getRoomId).collect(Collectors.toSet())
                : Set.of();
        return contacts.stream()
                .map(contact -> {
                    RoomBaseInfo room = rooms.get(contact.getRoomId());
                    RoomTypeEnum roomType = room.getType();
                    switch (roomType) {
                        case FILE_ASSISTANT:
                            // 文件助手只有本人发送的消息，不产生他人消息未读数。
                            return Pair.of(contact.getRoomId(), 0);
                        case GROUP:
                            if (!joinedGroupIds.contains(contact.getRoomId())) {
                                return Pair.of(contact.getRoomId(), 0);
                            }
                            break;
                        case FRIEND:
                            break;
                    }
                    long readId = Math.max(Optional.ofNullable(contact.getLastReadMsgId()).orElse(0L),
                            getContactMessageBoundary(contact));
                    long lastMsgId = Optional.ofNullable(room.getLastMsgId()).orElse(0L);
                    Integer unreadCount = messageDao.getUnReadCount(
                            roomType, contact.getRoomId(), uid, readId, lastMsgId);
                    return Pair.of(contact.getRoomId(), unreadCount);
                })
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
