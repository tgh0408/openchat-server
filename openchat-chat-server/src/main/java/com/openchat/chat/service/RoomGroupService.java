package com.openchat.chat.service;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.dao.MessageDao;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.openchat.chat.domain.entity.Contact;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.entity.msg.GroupInvitationMsgDTO;
import com.openchat.chat.domain.entity.msg.GroupRemovalMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.common.event.GroupInvitationEvent;
import com.openchat.common.event.GroupInfoUpdatedEvent;
import com.openchat.common.event.GroupMembersRemovedEvent;
import com.openchat.common.event.GroupRemovalMessageEvent;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.dao.RoomGroupDao;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.entity.RoomGroupExtra;
import com.openchat.chat.domain.enums.GroupRoleEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.AddGroupMembersReq;
import com.openchat.chat.domain.vo.req.RemoveGroupMembersReq;
import com.openchat.chat.domain.vo.req.CreateGroupReq;
import com.openchat.chat.domain.vo.req.UpdateGroupInfoReq;
import com.openchat.chat.service.adapter.ChatAdapter;
import com.openchat.common.utils.AssertUtil;
import com.openchat.user.dao.UserDao;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.domain.vo.resp.GroupResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.vo.resp.GroupMemberInfo;
import com.openchat.user.service.FriendService;
import com.openchat.user.service.adapter.GroupAdapter;
import com.openchat.user.service.adapter.GroupMemberAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomGroupService {
    private static final Long ALL_USER_ROOM_ID = 1L;

    final RoomDao roomDao;
    final RoomGroupDao roomGroupDao;
    final GroupMemberDao groupMemberDao;
    final ContactDao contactDao;
    final ApplicationEventPublisher applicationEventPublisher;
    private final UserDao userDao;
    private final RoomAccessService roomAccessService;
    private final MessageDao messageDao;
    private final RoomAppService roomAppService;

    /** 新用户入群、本人消息边界及可靠投递记录共用注册事务。 */
    @Transactional(rollbackFor = Exception.class)
    public void joinAllUserGroup(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BusinessException("全员群成员 ID 无效");
        }
        Room room = roomDao.get(ALL_USER_ROOM_ID, "全员群不存在");
        AssertUtil.isTrue(room.getType() == RoomTypeEnum.GROUP, "全员群房间类型不匹配");
        roomGroupDao.get(ALL_USER_ROOM_ID, "全员群不存在或已解散");

        GroupMember member = new GroupMember();
        member.setRoomId(ALL_USER_ROOM_ID);
        member.setUid(uid);
        member.setRole(GroupRoleEnum.NORMAL);
        if (!groupMemberDao.save(member)) {
            throw new BusinessException("加入全员群失败");
        }
        String content = invitationName(uid, userDao.getBatch(List.of(uid))) + " 进入了群聊";
        Message message = saveSystemMessage(uid, ALL_USER_ROOM_ID, systemMessageSummary(content),
                MessageExtra.builder().systemContent(content).build(), "保存入群提示失败");
        Contact contact = ChatAdapter.buildContact(uid, ALL_USER_ROOM_ID);
        contact.setLastDeleteId(message.getId() - 1);
        if (!contactDao.save(contact)) {
            throw new BusinessException("创建全员群会话失败");
        }
        applicationEventPublisher.publishEvent(new GroupInvitationEvent(message.getId()));
    }

    public GroupResp getGroupInfo(@Valid Long roomId, Long uid) {
        RoomGroup roomGroup = roomGroupDao.get(roomId);
        GroupMember groupMember = groupMemberDao.getMember(roomId, uid);
        if (groupMember == null || roomGroup == null) {
            throw new BusinessException("找不到对应的群组或者群成员信息");
        }
        return GroupAdapter.init(roomGroup, groupMember, uid);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupResp updateGroupInfo(UpdateGroupInfoReq request, Long uid) {
        Room room = roomDao.get(request.getRoomId(),"群聊不存在");
        AssertUtil.isTrue(room.getType() == RoomTypeEnum.GROUP, "群类型不匹配");

        RoomGroup roomGroup = roomGroupDao.get(request.getRoomId(),"群聊不存在或已解散");
        GroupMember operator = groupMemberDao.getMember(request.getRoomId(), uid);

        AssertUtil.isTrue(operator != null && operator.getRole() != null
                && GroupRoleEnum.isAdmin(operator.getRole()), "无管理员权限");

        AssertUtil.isTrue(request.hasUpdates(), "至少需要更新一个信息");

        String groupName = request.hasGroupName() ? request.getGroupName().trim() : null;
        String description = request.hasGroupDescription() ? request.getGroupDescription().trim() : null;
        RoomGroupExtra extra = roomGroup.getExtJson();
        String previousDescription = extra == null || extra.getNotice() == null
                ? null : extra.getNotice().getDescription();
        boolean nameChanged = groupName != null && !Objects.equals(groupName, roomGroup.getName());
        boolean noticeChanged = description != null && !Objects.equals(description, previousDescription);
        if (!nameChanged && !noticeChanged) return GroupAdapter.init(roomGroup, operator, uid);

        if (nameChanged) roomGroup.setName(groupName);
        if (noticeChanged) {
            if (extra == null) extra = new RoomGroupExtra();
            extra.setNotice(buildNotice(description, uid));
            roomGroup.setExtJson(extra);
        }
        // 同一版本仅允许一次更新；并发修改或解散后不能写入提示或恢复旧资料。
        if (!roomGroupDao.updateInfo(roomGroup)) {
            throw new BusinessException("群资料已变化或群聊已解散，请刷新后重试");
        }
        String operatorName = invitationName(uid, userDao.getBatch(List.of(uid)));
        if (nameChanged) publishInfoUpdate(uid, room.getId(), operatorName + " 修改了群名称为 " + groupName);
        if (noticeChanged) publishInfoUpdate(uid, room.getId(), operatorName + " 修改了群公告");
        return GroupAdapter.init(roomGroup, operator, uid);
    }

    /** 资料、系统提示及可靠投递记录共用业务事务，失败时一并回滚。 */
    private void publishInfoUpdate(Long uid, Long roomId, String content) {
        Message message = saveSystemMessage(uid, roomId, content,
                MessageExtra.builder().systemContent(content).build(), "保存群资料修改提示失败");
        applicationEventPublisher.publishEvent(new GroupInfoUpdatedEvent(message.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delGroup(Long uid, @NotNull Long roomId) {
        //判断群主是不是你 不是群主就无法解散
        if (!groupMemberDao.isSuperAdmin(roomId, uid)) {
            throw new BusinessException("此房间与你的权限不匹配,或者已被解散");
        }
        // TODO 解散群组全员通知
        roomGroupDao.delRoomGroup(uid, roomId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void exitGroup(Long uid, @NotNull Long roomId) {
        RoomGroup roomGroup = roomGroupDao.get(roomId,"群ID不存在");
        if (roomGroup.getUid().equals(uid)) {
            throw new BusinessException("群主无法退出此群,只可解散此群");
        }
        //校验你是否在这个群中 删了也表示不在这个群中了
        GroupMember groupMember = groupMemberDao.getMember(roomId, uid);
        if (groupMember == null) {
            throw new BusinessException("此群不存在或者已被删除");
        }
        groupMemberDao.delUserByUserId(uid, roomId);
    }

    public List<GroupMemberInfo> getGroupMembers(Long uid, Long roomId) {
        GroupMember member = groupMemberDao.getMember(roomId, uid);
        RoomGroup group = roomGroupDao.get(roomId);
        if (member == null || group == null) {
            throw new BusinessException("你不在当前房间内");
        }
        List<GroupMember> memberUidList = groupMemberDao.get(roomId).stream()
                .sorted(FriendService.groupMemberComparator())
                .toList();
        List<Long> ids = memberUidList.stream()
                .map(GroupMember::getUid)
                .collect(Collectors.toList());
        Map<Long, User> infoCacheBatch = userDao.getBatch(ids);
        return GroupMemberAdapter.getGroupMembers(memberUidList, infoCacheBatch);
    }

    /** 新房间、实际成员、会话及邀请结果提示一起提交，任一写入失败整体回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public GroupResp createGroup(@Valid CreateGroupReq request, Long uid) {
        AssertUtil.fastFailValidate(request);
        List<Long> targets = request.getUids().stream().distinct().sorted().toList();
        if (targets.contains(uid)) {
            throw new BusinessException("受邀成员不能包含自己");
        }
        List<Long> requestedUids = new ArrayList<>();
        requestedUids.add(uid);
        requestedUids.addAll(targets);
        Map<Long, User> profiles = userDao.getBatch(requestedUids);
        for (Long requestedUid : requestedUids) {
            if (profiles.get(requestedUid) == null) {
                throw new BusinessException("用户不存在，请刷新后重试");
            }
        }
        List<Long> accepted = new ArrayList<>();
        List<Long> rejected = new ArrayList<>();
        for (Long target : targets) {
            if (roomAccessService.isFriend(uid, target)) accepted.add(target);
            else rejected.add(target);
        }
        AssertUtil.isNotEmpty(accepted, "没有足够满足条件的好友创建群聊");

        accepted.add(uid);

        Room room = ChatAdapter.buildRoom(RoomTypeEnum.GROUP);
        if (!roomDao.save(room)) {
            throw new BusinessException("创建群聊房间失败");
        }

        RoomGroup roomGroup = ChatAdapter.buildGroupRoom(room.getId());
        roomGroup.setUid(uid);
        roomGroup.setExtJson(new RoomGroupExtra());
        if (!roomGroupDao.save(roomGroup)) {
            throw new BusinessException("创建群资料失败");
        }

        GroupMember owner = null;
        for (Long memberUid : accepted) {
            GroupMember member = new GroupMember();
            member.setRoomId(room.getId());
            member.setUid(memberUid);
            member.setRole(Objects.equals(memberUid, uid) ? GroupRoleEnum.SUPER_ADMIN : GroupRoleEnum.NORMAL);
            if (!groupMemberDao.save(member)) {
                throw new BusinessException("创建群成员失败");
            }
            if (Objects.equals(memberUid, uid)) owner = member;
        }
        if (!accepted.isEmpty()) publishInvitation(uid, room.getId(), accepted);
        // 拒绝提示排在成功入群提示之后，保证实际成员的历史下界不会过滤这些结果。
        publishInvitationRefusals(uid, room.getId(), rejected, profiles);

        GroupResp response = GroupAdapter.init(roomGroup, owner, uid);
        response.setMemberCount(accepted.size());
        // 沿用会话列表按角色、更新时间和成员 ID 选取前九人的头像规则。
        response.setAvatarUidList(roomAppService.getRoomBaseInfoMap(List.of(room), uid)
                .get(room.getId()).getAvatarUidList());
        return response;
    }

    /** 使用服务端时间和认证用户记录最新群公告，避免客户端伪造审计字段。 */
    private RoomGroupExtra.Notice buildNotice(String description, Long uid) {
        RoomGroupExtra.Notice notice = new RoomGroupExtra.Notice();
        notice.setDescription(description.trim());
        notice.setCreateTime(new Date());
        notice.setUid(uid);
        return notice;
    }

    /** 成员、会话、系统提示及可靠投递记录在同一事务提交。 */
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long uid, @Valid AddGroupMembersReq request) {
        Long roomId = request.getRoomId();
        // 多节点添加、退出、解散统一按 room -> room_group -> member 顺序加锁。
        Room room = roomDao.get(roomId, "群不存在");
        roomGroupDao.get(roomId,"群不存在");
        if (room.getType() != RoomTypeEnum.GROUP) {
            throw new BusinessException("群不存在");
        }
        if (groupMemberDao.getMember(roomId, uid) == null) {
            throw new BusinessException("你不在当前群聊中");
        }
        Set<Long> targets = new HashSet<>(request.getUids());
        if (targets.contains(uid)) throw new BusinessException("不能添加自己");
        // 先校验整批好友；任一失效则整批回滚，不产生部分添加。
        for (Long target : targets) {
            if (!roomAccessService.isFriend(uid, target)) {
                throw new BusinessException("只能添加当前通讯录中未拉黑的好友");
            }
        }
        List<Long> added = new ArrayList<>();
        for (Long target : targets) {
            GroupMember existing = groupMemberDao.getMember(roomId, target);
            if (existing != null && existing.getDeleteStatus() == YesOrNoEnum.NO) continue;
            if (existing == null) {
                GroupMember member = new GroupMember();
                member.setRoomId(roomId);
                member.setUid(target);
                member.setRole(GroupRoleEnum.NORMAL);
                member.setDeleteStatus(YesOrNoEnum.NO);
                if (!groupMemberDao.save(member)) continue;
            } else {
                groupMemberDao.restoreMember(existing);
            }
            added.add(target);
        }
        if (added.isEmpty()) return;
        publishInvitation(uid, roomId, added);
    }

    /** 与添加、退出和解散按相同锁顺序串行化，整批核验后才修改成员关系。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeMembers(Long uid, @Valid RemoveGroupMembersReq request) {
        Long roomId = request.getRoomId();
        Room room = roomDao.get(roomId, "群不存在");
        if (room.getType() != RoomTypeEnum.GROUP) {
            throw new BusinessException("群类型不匹配");
        }
        RoomGroup group = roomGroupDao.get(roomId, "群不存在或已解散");
        GroupMember operator = groupMemberDao.getMember(roomId, uid);
        if (operator == null || operator.getRole() == null) throw new BusinessException("无管理员权限");
        boolean owner;
        switch (operator.getRole()) {
            case SUPER_ADMIN -> owner = true;
            case ADMIN -> owner = false;
            default -> throw new BusinessException("无管理员权限");
        }
        List<Long> targets = request.getUids().stream().distinct().sorted().toList();
        if (targets.contains(uid)) throw new BusinessException("不能移出自己");
        List<GroupMember> removed = new ArrayList<>();
        for (Long target : targets) {
            if (Objects.equals(group.getUid(), target)) throw new BusinessException("不能移出群主");
            GroupMember member = groupMemberDao.getMember(roomId, target);
            // 重复请求跳过已退出成员；不恢复或删除其历史与会话状态。
            if (member == null) continue;
            if (!(member.getRole() == GroupRoleEnum.NORMAL
                    || (owner && member.getRole() == GroupRoleEnum.ADMIN))) {
                throw new BusinessException("无权移出该群成员");
            }
            removed.add(member);
        }
        if (!removed.isEmpty()) {
            List<Long> users = new ArrayList<>(removed.stream().map(GroupMember::getUid).toList());
            users.add(uid);
            Map<Long, User> profiles = userDao.getBatch(users);
            String operatorName = invitationName(uid, profiles);
            for (GroupMember member : removed) {
                String memberName = invitationName(member.getUid(), profiles);
                String content = memberName + " 被 " + operatorName + " 移出群聊";
                Message message = saveSystemMessage(uid, roomId, systemMessageSummary(content),
                        MessageExtra.builder().systemContent(content)
                                .groupRemoval(new GroupRemovalMsgDTO(
                                        "group-removal", operatorName, member.getUid(), memberName))
                                .build(), "保存移出群聊提示失败");
                applicationEventPublisher.publishEvent(new GroupRemovalMessageEvent(message.getId()));
            }
        }
        for (GroupMember member : removed) {
            groupMemberDao.removeMember(member);
        }
        if (!removed.isEmpty()) {
            applicationEventPublisher.publishEvent(new GroupMembersRemovedEvent(
                    roomId, removed.stream().map(GroupMember::getUid).toList()));
        }
    }

    /** 每位实际新增成员独立生成消息及可靠事件；任一失败由外层整批事务回滚。 */
    private void publishInvitation(Long uid, Long roomId, List<Long> added) {
        List<Long> users = new ArrayList<>(added);
        users.add(uid);
        Map<Long, User> profiles = userDao.getBatch(users);
        String inviterName = invitationName(uid, profiles);
        for (Long memberUid : added) {
            String memberName = invitationName(memberUid, profiles);
            String content = inviterName + " 邀请 " + memberName + " 进入了群聊";
            Message message = saveSystemMessage(uid, roomId, systemMessageSummary(content),
                    MessageExtra.builder().systemContent(content)
                            .groupInvitation(new GroupInvitationMsgDTO("group-invitation", inviterName, memberName))
                            .build(), "保存入群提示失败");
            contactDao.advanceInvitationBoundary(memberUid, roomId, message.getId());
            applicationEventPublisher.publishEvent(new GroupInvitationEvent(message.getId()));
        }
    }

    /** 拒绝者不加入成员或会话；提示只经现有系统消息链路向实际群成员投递。 */
    private void publishInvitationRefusals(Long uid, Long roomId, List<Long> rejected,
                                          Map<Long, User> profiles) {
        for (Long target : rejected) {
            String content = invitationName(target, profiles) + " 拒绝加入群聊";
            Message message = saveSystemMessage(uid, roomId, systemMessageSummary(content),
                    MessageExtra.builder().systemContent(content).build(), "保存拒绝入群提示失败");
            applicationEventPublisher.publishEvent(new GroupInvitationEvent(message.getId()));
        }
    }

    /** 统一构造并保存群系统消息；边界推进和事件发布由各业务场景按原顺序完成。 */
    private Message saveSystemMessage(Long uid, Long roomId, String summary,
                                      MessageExtra extra, String failureMessage) {
        Date now = new Date();
        Message message = Message.builder()
                .id(IdWorker.getId()).roomId(roomId).fromUid(uid)
                .type(MessageTypeEnum.SYSTEM).status(YesOrNoEnum.NO)
                .content(summary).extra(extra)
                .createTime(now).updateTime(now).build();
        if (!messageDao.save(RoomTypeEnum.GROUP, message)) {
            throw new BusinessException(failureMessage);
        }
        return message;
    }

    /** 成员变更提示按 Unicode 码点截取摘要，完整正文仍保留在扩展字段中。 */
    private String systemMessageSummary(String content) {
        if (content.codePointCount(0, content.length()) <= 1024) return content;
        return content.substring(0, content.offsetByCodePoints(0, 1021)) + "...";
    }

    /** 群内广播只使用公开昵称，不泄露邀请者的私有好友备注。 */
    private String invitationName(Long uid, Map<Long, User> profiles) {
        User user = profiles.get(uid);
        if (user == null || user.getName() == null || user.getName().isBlank()) return "用户 " + uid;
        return user.getName();
    }
}
