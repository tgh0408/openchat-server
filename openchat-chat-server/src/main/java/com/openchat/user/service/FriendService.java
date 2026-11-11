package com.openchat.user.service;

import com.openchat.chat.domain.enums.GroupRoleEnum;
import cn.hutool.core.collection.CollectionUtil;
import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.RoomGroupDao;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.vo.req.DeleteContactReq;
import com.openchat.chat.service.ChatService;
import com.openchat.chat.service.ContactService;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.chat.service.adapter.MessageAdapter;
import com.openchat.common.annotation.RedissonLock;
import com.openchat.common.domain.enums.MsgSourceEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.domain.vo.req.CursorPageBaseReq;
import com.openchat.common.domain.vo.req.PageBaseReq;
import com.openchat.common.domain.vo.resp.CursorPageBaseResp;
import com.openchat.common.domain.vo.resp.FriendResp;
import com.openchat.common.domain.vo.resp.GroupResp;
import com.openchat.common.domain.vo.resp.PageBaseResp;
import com.openchat.common.event.UserApplyEvent;
import com.openchat.common.event.FriendApplyChangedEvent;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.AssertUtil;
import com.openchat.common.service.MinioBucketService;
import com.openchat.user.domain.dto.FriendRemarkData;
import com.openchat.user.domain.vo.resp.*;
import com.openchat.user.service.adapter.FriendRemarkAdapter;
import jakarta.validation.Validator;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.user.dao.UserApplyDao;
import com.openchat.user.dao.UserDao;
import com.openchat.user.dao.UserFriendDao;
import com.openchat.user.domain.entity.RoomFriend;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.entity.UserApply;
import com.openchat.user.domain.entity.UserFriend;
import com.openchat.user.domain.dto.FriendPageRow;
import com.openchat.user.domain.dto.FriendApplySettings;
import com.openchat.user.domain.dto.ApplyChat;
import com.openchat.user.domain.enums.ApplyStatusEnum;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.user.domain.vo.req.*;
import jakarta.validation.constraints.NotNull;
import com.openchat.user.service.adapter.FriendAdapter;
import com.openchat.user.service.adapter.GroupAdapter;
import com.openchat.user.service.adapter.NameSortKey;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 用户联系人、好友申请及群成员资料服务。 */
@Service
@RequiredArgsConstructor
public class FriendService {
    private static final Log log = LogFactory.getLog(FriendService.class);
    final ApplicationEventPublisher applicationEventPublisher;
    final UserFriendDao userFriendDao;
    final UserDao userDao;
    final UserApplyDao userApplyDao;
    final RoomFriendDao roomFriendDao;
    final RoomFriendService roomFriendService;
    final GroupMemberDao groupMemberDao;
    final RoomGroupDao roomGroupDao;
    final ChatService chatService;
    final MinioBucketService minioBucketService;
    final RoomAccessService roomAccessService;
    final ContactService contactService;
    final Validator validator;

    /** 群头像预览按角色、入群更新时间和成员 ID 保持稳定顺序，避免多实例返回顺序抖动。 */
    public static Comparator<GroupMember> groupMemberComparator() {
        return Comparator.comparing(
                        GroupMember::getRole,
                        Comparator.nullsLast(Comparator.comparingInt(GroupRoleEnum::getRole)))
                .thenComparing(
                        GroupMember::getUpdateTime,
                        Comparator.nullsLast(Date::compareTo))
                .thenComparing(
                        GroupMember::getId,
                        Comparator.nullsLast(Long::compareTo));
    }

    /** 文本备注一次提交；版本条件裁决跨实例并发，不新增关系锁。 */
    public FriendRemarkResp saveRemark(Long uid, FriendRemarkReq request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BusinessException("备注表单参数无效");
        }
        UserFriend original = roomAccessService.requireFriendRelation(uid, request.getRoomId());
        if (!Objects.equals(original.getUid(), uid)
                || !Objects.equals(original.getFriendUid(), request.getTargetUid())) {
            throw new BusinessException("好友对象与当前房间不匹配");
        }
        if (!Objects.equals(original.getVersion(), request.getVersion())) {
            throw new BusinessException("备注已被修改，请重新打开后编辑");
        }
        String remark = request.getRemark().strip();
        String sortKey = NameSortKey.build(remark);
        UserFriend updated = UserFriend.builder().id(original.getId()).uid(uid)
                .friendUid(original.getFriendUid()).version(request.getVersion())
                .nickname(sortKey == null ? null : remark).nicknameSortKey(sortKey).build();
        updated.setTags(new FriendRemarkData(
                request.getTags().stream().map(String::strip).distinct().toList(),
                request.getPhones().stream().map(String::strip).distinct().toList(),
                request.getDescription().strip(), List.of()));
        if (!userFriendDao.updateRemark(updated)) {
            throw new BusinessException("好友关系或备注已变化，请重新打开后编辑");
        }
        return FriendRemarkAdapter.snapshot(updated);
    }

    public FriendCheckResp check(Long uid, FriendCheckReq friendCheckReq) {
        Set<Long> friendUidSet = roomAccessService.friendUids(uid, friendCheckReq.getUidList());
        //遍历请求中的uid集合，如果为好友的uid集合中包含，则设置为true，否则设置为false
        List<FriendCheckResp.FriendCheck> friendCheckList = friendCheckReq.getUidList().stream().map(friendUid -> {
            FriendCheckResp.FriendCheck friendCheck = new FriendCheckResp.FriendCheck();
            friendCheck.setUid(friendUid);
            friendCheck.setIsFriend(friendUidSet.contains(friendUid));
            return friendCheck;
        }).collect(Collectors.toList());
        return new FriendCheckResp(friendCheckList);
    }

    public CursorPageBaseResp<FriendResp> friendList(Long uid, CursorPageBaseReq request) {
        CursorPageBaseResp<FriendPageRow> friendPage = userFriendDao.getFriendPage(uid, request);
        if (CollectionUtils.isEmpty(friendPage.getList())) {
            return CursorPageBaseResp.init(friendPage, List.of());
        }
        List<Long> friendUidList = friendPage.getList()
                .stream().map(FriendPageRow::getFriendUid)
                .toList();
        //好友与房间的映射关系
        List<RoomFriend> roomFriendList = roomFriendDao.getFriendRoomMap(uid, friendUidList);
        Map<Long, Long> friendRoomMap = roomFriendList.stream().collect(Collectors.toMap(
                room -> uid.equals(room.getUid1()) ? room.getUid2() : room.getUid1(),
                RoomFriend::getRoomId));
        return CursorPageBaseResp.init(friendPage, FriendAdapter.buildFriend(friendPage.getList(), friendRoomMap));
    }
    /** 按真实房间类型查询资料，沿用 DAO 缓存及访问权限校验，不额外开启事务。 */
    public FriendDetailResp userMemberDetail(Long uid, Long targetUid, Long roomId) {
        Room room = roomAccessService.requireCurrentMember(uid, roomId);
        switch (room.getType()) {
            case FRIEND:
                RoomFriend friendRoom = roomFriendDao.get(roomId);
                if (!Objects.equals(roomAccessService.requireFriendPeer(uid, roomId), targetUid)) {
                    throw new BusinessException("目标用户不属于当前单聊房间");
                }
                UserFriend friend = userFriendDao.getRelation(uid, targetUid);
                User user = userDao.get(IdTypeEnum.ID , targetUid, "对方用户不存在");
                return buildFriendDetail(user, friend, friendRoom);
            case GROUP:
                return groupMemberDetail(uid, targetUid, roomId);
            default:
                throw new BusinessException("当前房间不支持查询好友或群成员资料");
        }
    }

    /** 群成员资料沿用好友详情协议，但只允许当前群成员查看，不能借接口枚举任意用户。 */
    public FriendDetailResp groupMemberDetail(Long uid, Long targetUid, Long roomId) {
        if (groupMemberDao.getMember(roomId, uid) == null
                || groupMemberDao.getMember(roomId, targetUid) == null
                || roomGroupDao.get(roomId) == null) {
            throw new BusinessException("群成员不存在或你不在当前群中");
        }
        User user = userDao.get(IdTypeEnum.ID, targetUid,"群成员资料不可用");
        UserFriend friend = userFriendDao.getRelation(uid, targetUid);
        if (friend != null && (friend.getDeleteStatus() == YesOrNoEnum.NO
                || friend.getBlackStatus() == YesOrNoEnum.YES)) {
            String pairKey = Math.min(uid, targetUid) + RoomFriendService.SPLIT + Math.max(uid, targetUid);
            RoomFriend room = roomFriendDao.get(pairKey);
            return buildFriendDetail(user, friend, room);
        }
        // 非好友不返回私有备注、好友添加时间或可操作的单聊房间。
        return buildFriendDetail(user, null, null);
    }

    /** 拉黑与删除独立；删除关系不返回私有字段，保留已拉黑对象的真实单聊供解除操作。 */
    private FriendDetailResp buildFriendDetail(User user, UserFriend relation, RoomFriend room) {
        UserFriend friend = relation != null && relation.getDeleteStatus() == YesOrNoEnum.NO ? relation : null;
        boolean blacklisted = relation != null && relation.getBlackStatus() == YesOrNoEnum.YES;
        Long roomId = room != null && (friend != null || blacklisted) ? room.getRoomId() : null;
        FriendDetailResp detail = FriendAdapter.buildDetail(user, friend, roomId);
        detail.setAvatar(minioBucketService.toAvatarUrl(detail.getAvatar()));
        detail.setBlacklisted(blacklisted);
        return detail;
    }

    public List<GroupResp> groupList(Long uid) {
        List<GroupMember> groupMembers = groupMemberDao.getUserRoom(uid);
        // 根据房间号查询出房间信息
        List<Long> roomIds = groupMembers.stream().map(GroupMember::getRoomId).toList();
        //新号通常没有好友列表为空
        if (roomIds.isEmpty()) return Collections.emptyList();
        // 拿到房间详情 这里过滤掉了解散掉的群
        Collection<RoomGroup> roomGroups = roomGroupDao.getBatch(roomIds).values();
        // 将 groupMember 转换成map 方便合并
        Map<Long, GroupMember> groupMembersMapper;
        try {
            //如果同个群room下出现一个角色同时拥有多个权限就会报错,此时去查数据库
            groupMembersMapper = groupMembers.stream()
                    .collect(Collectors
                            .toMap(GroupMember::getRoomId, Function.identity())
                    );
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new BusinessException("Error - 001: 出现无法解决的脏数据,请联系管理员处理");
        }
        List<GroupResp> groupList = GroupAdapter.init(roomGroups, groupMembersMapper, uid);
        Map<Long, List<GroupMember>> roomMemberMap = groupMemberDao.getBatch(new ArrayList<>(roomIds));
        Map<Long, List<GroupMember>> orderedMembers = roomMemberMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .sorted(groupMemberComparator())
                                .toList()));
        groupList.forEach(group -> {
            List<GroupMember> members = orderedMembers.getOrDefault(group.getRoomId(), List.of());
            group.setMemberCount(members.size());
            group.setAvatarUidList(members.stream()
                    .limit(9)
                    .map(GroupMember::getUid)
                    .toList());
        });
        return groupList;
    }

    @Transactional(rollbackFor = Exception.class)
    public FriendApplyResultResp apply(Long uid, FriendApplyReq applyReq) {
        Long targetUid = applyReq.getTargetUid();
        AssertUtil.isFalse(uid.equals(targetUid), "不能添加自己为好友");
        if (roomAccessService.isMyFriendBlacklisted(uid, targetUid)) {
            throw new BusinessException("你已将对方拉黑，请解除黑名单后再操作");
        }
        if (roomAccessService.isMyFriendBlacklisted(targetUid, uid)) {
            throw new BusinessException("对方已将你拉黑，无法发起好友申请");
        }
        if (roomAccessService.hasMutualFriendRelation(uid, targetUid)) {
            throw new BusinessException("你们已经是好友了");
        }
        UserFriend retained = roomAccessService.findAvailableFriend(targetUid, uid);
        if (retained != null && roomAccessService.findFriendRelation(uid, targetUid) == null) {
            String pairKey = Math.min(uid, targetUid) + RoomFriendService.SPLIT + Math.max(uid, targetUid);
            RoomFriend room = roomFriendDao.get(pairKey, "原单聊房间不存在，请联系管理员");
            if (!Objects.equals(roomAccessService.requireFriendPeer(uid, room.getRoomId()), targetUid)) {
                throw new BusinessException("好友房间不匹配");
            }
            if (!userFriendDao.restoreRetainedFriend(uid, retained,
                    FriendAdapter.buildApplySettings(applyReq))) {
                throw new BusinessException("好友关系已变化，请刷新后重试");
            }
            // 直接恢复不读写好友申请，只返回结果供客户端本地补入联系人，不发送双方通知或欢迎消息。
            String sortKey = Optional.ofNullable(NameSortKey.build(applyReq.getRemark()))
                    .orElseGet(() -> userDao.get(IdTypeEnum.ID, targetUid,"对方用户不存在").getNameSortKey());
            return new FriendApplyResultResp("ADDED", targetUid, room.getRoomId(),
                    NameSortKey.indexLetter(sortKey));
        }
        UserApply lastApply = Optional.ofNullable(userApplyDao.getLastApply(uid, targetUid)).orElse(new UserApply());
        if (lastApply.getStatus() == ApplyStatusEnum.WAIT_APPROVE) {
            if (!userApplyDao.appendPendingMessage(lastApply, new ApplyChat(uid, applyReq.getMessage()), true)) {
                throw new BusinessException("好友申请状态已变化，请刷新后重试");
            }
            applicationEventPublisher.publishEvent(new UserApplyEvent(this, lastApply));
            return null;
        }
        UserApply insert = FriendAdapter.buildFriendApply(uid, applyReq, lastApply);
        UserApply incoming = userApplyDao.getByIdApplyId(targetUid, uid);
        if (incoming != null) {
            // 使用数据库中的真实申请 ID，不信任调用方传来的 applyId。
            insert.setStatus(ApplyStatusEnum.APPROVED);
            if (!userApplyDao.save(insert)) throw new BusinessException("保存好友申请失败");
            completeApproval(uid, incoming, FriendAdapter.buildApplySettings(applyReq));
            return null;
        }
        if (!userApplyDao.save(insert)) throw new BusinessException("保存好友申请失败");
        applicationEventPublisher.publishEvent(new UserApplyEvent(this, insert));
        return null;
    }

    /** 双方均可回复并置对方未读；双方可靠通知与消息写入同一事务，不新建好友关系。 */
    @Transactional(rollbackFor = Exception.class)
    public void replyApply(Long uid, FriendReplyReq request) {
        UserApply apply = userApplyDao.getParticipantPending(uid, request.getApplyId());
        if (apply == null) throw new BusinessException("申请不存在或已处理，无法回复");
        Long peerUid = FriendAdapter.friendApplyPeerUid(uid, apply);
        if (roomAccessService.isMyFriendBlacklisted(uid, peerUid)
                || roomAccessService.isMyFriendBlacklisted(peerUid, uid)) {
            throw new BusinessException("存在黑名单关系，无法回复好友申请");
        }
        if (!userApplyDao.appendPendingMessage(apply, new ApplyChat(uid, request.getMsg()), uid.equals(apply.getUid()))) {
            throw new BusinessException("好友申请或消息已变化，请刷新后重试");
        }
        applicationEventPublisher.publishEvent(new FriendApplyChangedEvent(this, peerUid));
        applicationEventPublisher.publishEvent(new FriendApplyChangedEvent(this, uid));
    }

    /** 按请求选项删除好友及本人的会话，不返回额外业务数据。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long uid, Long roomId, boolean clearHistory) {
        UserFriend friend = roomAccessService.requireFriendRelation(uid, roomId);
        if (clearHistory) {
            contactService.deleteContact(DeleteContactReq.builder().roomId(roomId).build(), friend.getUid());
        }
        friend.setUpdateTime(new Date());
        friend.setDeleteStatus(YesOrNoEnum.YES);
        if (!userFriendDao.updateById(friend)) {
            throw new BusinessException("好友关系已变化，请刷新后重试");
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public PageBaseResp<FriendApplyResp> pageApplyFriend(Long uid, PageBaseReq request) {
        IPage<UserApply> userApplyIPage = userApplyDao.friendApplyPage(uid, request);
        if (CollectionUtil.isEmpty(userApplyIPage.getRecords())) {
            return PageBaseResp.init(userApplyIPage, List.of());
        }
        // 仅沿用名称补全；头像及其他资料仍由客户端按 peerUid 查询。
        List<Long> ids = userApplyIPage.getRecords().stream()
                .map(apply -> FriendAdapter.friendApplyPeerUid(uid, apply)).distinct().toList();
        return PageBaseResp.init(userApplyIPage,
                FriendAdapter.buildFriendApplyList(uid, userApplyIPage.getRecords(), userDao.getBatch(ids)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyApprove(Long uid, FriendApproveReq request) {
        //applyId 为好友申请id 非用户id
        //获取最新的请求记录
        UserApply lastApply = userApplyDao.getLastNeedApply(uid, request.getApplyId());
        //如果最新记录不是待审批状态
        if (lastApply.getStatus() != ApplyStatusEnum.WAIT_APPROVE) {
            throw new BusinessException("该好友没有需要通过的申请");
        }
        //如果apply的数据与传入的目标id不相等就是数据错乱了
        if (!lastApply.getUid().equals(request.getFriendId())) {
            throw new BusinessException("apply的数据与传入的目标id不相等,请联系管理员");
        }
        //是申请人的id
        userDao.get(IdTypeEnum.ID, lastApply.getUid(),"用户id{" + request.getFriendId() + "}不存在");
        //判断是否已经为好友关系,你的列表有他，他的列表有你 是否拉黑对方
        roomAccessService.requireCanAddFriend(uid, lastApply.getUid());
        FriendApprovalSettingsReq settings = request.getSettings();
        FriendApplySettings approvalSettings;
        if (settings != null) {
            approvalSettings = new FriendApplySettings(
                    settings.getRemark().strip(),
                    settings.getTags().stream().map(String::strip).distinct().toList(),
                    settings.getPermission(), settings.getHideMine(), settings.getHideTheirs());
        }else {
            approvalSettings = null;
        }
        completeApproval(uid, lastApply, approvalSettings);
    }

    /** 普通审批与互相申请共用原事务；先原子转换申请、补齐关系，再分别保存双方私有设置。 */
    private void completeApproval(Long uid, UserApply lastApply, FriendApplySettings settings) {
        if (!userApplyDao.agree(lastApply)) throw new BusinessException("申请状态已变化，请刷新");
        userFriendDao.ensureMutualFriend(uid, lastApply.getUid());
        if (lastApply.getRequestSettings() != null
                && !userFriendDao.applySettings(lastApply.getUid(), uid, FriendAdapter.readApplySettings(lastApply))) {
            throw new BusinessException("保存申请人好友设置失败，请刷新后重试");
        }
        if (settings != null && !userFriendDao.applySettings(uid, lastApply.getUid(), settings)) {
            throw new BusinessException("保存好友设置失败，请刷新后重试");
        }
        applicationEventPublisher.publishEvent(new FriendApplyChangedEvent(this, uid));
        //创建一个聊天房间
        RoomFriend roomFriend = roomFriendService.createFriendRoom(Arrays.asList(uid, lastApply.getUid()));
        //发送一条同意消息。。我们已经是好友了，开始聊天吧
        chatService.sendMsg(MessageAdapter.buildAgreeMsg(roomFriend.getRoomId()), uid, MsgSourceEnum.CLIENT);
    }


    @Transactional(rollbackFor = Exception.class)
    @RedissonLock(key = "#uid")
    public void applyRefuse(Long uid, FriendRefuseReq request) {
        //判断好友id是否存在
        userDao.get(IdTypeEnum.ID, request.getRefuseId(),"用户id{" + request.getRefuseId() + "}不存在");
        //判断是否已经为好友关系,你的列表有他，他的列表有你
        if (roomAccessService.hasMutualFriendRelation(uid, request.getRefuseId())) {
            throw new BusinessException("已存在好友关系，无法拒绝好友申请");
        }
        //获取最新的请求记录
        UserApply userApply = userApplyDao.getLastNeedApply(uid, request.getApplyId());
        if (!Objects.equals(userApply.getUid(), request.getRefuseId())) {
            throw new BusinessException("好友申请不属于当前用户");
        }
        //如果最新记录不是待审批状态
        if (userApply.getStatus() != ApplyStatusEnum.WAIT_APPROVE) {
            throw new BusinessException("没有申请被拒绝");
        }
        //拒绝申请
        if (!userApplyDao.refuseApply(userApply)) throw new BusinessException("申请状态已变化，请刷新");
        applicationEventPublisher.publishEvent(new FriendApplyChangedEvent(this, uid));
    }

    @Transactional(rollbackFor = Exception.class)
    public void blackFriend(Long uid, @NotNull Long roomId) {
        Long friendId = roomAccessService.requireFriendPeer(uid, roomId);
        UserFriend userFriend = userFriendDao.getRelation(uid, friendId);
        if (userFriend == null) {
            // 非好友拉黑只建立删除状态的单向记录，不加入通讯录。
            UserFriend created = UserFriend.builder().uid(uid).friendUid(friendId)
                    .blackStatus(YesOrNoEnum.YES).deleteStatus(YesOrNoEnum.YES).version(0).build();
            try {
                if (!userFriendDao.save(created)) throw new BusinessException("拉黑失败，请刷新后重试");
            } catch (DuplicateKeyException exception) {
                throw new BusinessException("好友关系已变化，请刷新后重试");
            }
        } else {
            if (userFriend.getBlackStatus() == YesOrNoEnum.YES) {
                throw new BusinessException("你已拉黑对方,请勿重复提交");
            }
            UserFriend updated = UserFriend.builder().id(userFriend.getId()).uid(uid).friendUid(friendId)
                    .version(userFriend.getVersion()).blackStatus(YesOrNoEnum.YES)
                    .updateTime(new Date()).build();
            if (!userFriendDao.updateById(updated)) {
                throw new BusinessException("好友关系已变化，请刷新后重试");
            }
        }
        if (userApplyDao.blackUser(uid, friendId)) {
            applicationEventPublisher.publishEvent(new FriendApplyChangedEvent(this, uid));
        }
    }

    /** 解除仅要求单聊参与者身份，不要求任一方向仍是好友，也不恢复删除状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void unBlackFriend(Long uid, Long roomId) {
        Long friendId = roomAccessService.requireFriendPeer(uid, roomId);
        // 包含非好友拉黑产生的删除记录，不能改用只返回通讯录好友的查询。
        UserFriend relation = userFriendDao.getRelation(uid, friendId);
        if (relation == null) {
            // 缺失记录只补建未拉黑的删除状态，不恢复好友关系。
            UserFriend created = UserFriend.builder().uid(uid).friendUid(friendId)
                    .blackStatus(YesOrNoEnum.NO).deleteStatus(YesOrNoEnum.YES).version(0).build();
            try {
                if (!userFriendDao.save(created)) throw new BusinessException("取消拉黑失败，请刷新后重试");
            } catch (DuplicateKeyException exception) {
                throw new BusinessException("好友关系已变化，请刷新后重试");
            }
        } else {
            if (relation.getBlackStatus() != YesOrNoEnum.YES) return;
            UserFriend updated = UserFriend.builder().id(relation.getId()).uid(uid).friendUid(friendId)
                    .version(relation.getVersion()).blackStatus(YesOrNoEnum.NO)
                    .updateTime(new Date()).build();
            if (!userFriendDao.updateById(updated)) {
                throw new BusinessException("好友关系已变化，请刷新后重试");
            }
        }
    }

    public List<FriendUnRead> selectUnreadApply(Long uid) {
        AssertUtil.isNonNull(uid, "用户id不存在");
        return userApplyDao.getUnreadApplies(uid).stream()
                .map(apply -> new FriendUnRead(FriendAdapter.friendApplyPeerUid(uid, apply), apply.getStatus()))
                .toList();
    }

    /** 只将当前用户点击的申请在本人方向标记已读，并通知其余客户端刷新未读状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void readApply(Long uid, @NotNull Long applyId) {
        if (userApplyDao.readApply(uid, applyId)) {
            applicationEventPublisher.publishEvent(new FriendApplyChangedEvent(this, uid));
        }
    }
}
