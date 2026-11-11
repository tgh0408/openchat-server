package com.openchat.user.service.adapter;

import com.openchat.common.domain.vo.resp.FriendResp;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.user.domain.dto.ApplyChat;
import com.openchat.user.domain.dto.FriendPageRow;
import com.openchat.user.domain.dto.FriendDetailRow;
import com.openchat.user.domain.dto.FriendApplySettings;
import com.openchat.user.domain.vo.resp.FriendDetailResp;
import com.openchat.user.domain.entity.UserApply;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.entity.UserFriend;
import com.openchat.user.domain.enums.ApplyReadEnum;
import com.openchat.user.domain.enums.ApplyStatusEnum;
import com.openchat.user.domain.enums.ApplyTypeEnum;
import com.openchat.user.domain.vo.req.FriendApplyReq;
import com.openchat.user.domain.vo.resp.FriendApplyResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.text.Normalizer;

@Slf4j
public class FriendAdapter {

    /** 从已有缓存快照组装资料；没有有效好友关系时只返回公开字段。 */
    public static FriendDetailResp buildDetail(User user, UserFriend friend, Long roomId) {
        FriendDetailRow row = new FriendDetailRow();
        row.setUid(user.getId());
        row.setName(user.getName());
        row.setAvatar(user.getAvatar());
        row.setSex(user.getSex() == null ? null : user.getSex().getValue());
        row.setPersonalitySignature(user.getPersonalitySignature());
        if (friend != null) {
            row.setRemark(friend.getNickname());
            row.setAddedAt(friend.getCreateTime());
        }
        FriendDetailResp detail = buildDetail(row, roomId);
        if (friend != null) detail.setRemarkSettings(FriendRemarkAdapter.snapshot(friend));
        return detail;
    }

    /** 保留公开名称与私有备注的区别，时间只使用真实关系创建时间。 */
    public static FriendDetailResp buildDetail(FriendDetailRow row, Long roomId) {
        FriendDetailResp detail = new FriendDetailResp();
        detail.setUid(row.getUid());
        detail.setRoomId(roomId);
        String name = nonBlank(row.getName());
        detail.setName(name == null ? "用户 " + row.getUid() : name);
        detail.setRemark(nonBlank(row.getRemark()));
        detail.setDisplayName(detail.getRemark() == null ? detail.getName() : detail.getRemark());
        detail.setAvatar(nonBlank(row.getAvatar()));
        detail.setSex(row.getSex());
        detail.setPersonalitySignature(nonBlank(row.getPersonalitySignature()));
        detail.setAddedAt(row.getAddedAt());
        return detail;
    }

    public static List<FriendResp> buildFriend(List<FriendPageRow> userFriends,
                                              Map<Long, Long> friendRoomMap) {
        return userFriends.stream().map(friend -> {
            if (friend.getProfileUid() == null) {
                log.warn("好友资料缺失: uid={}, friendUid={}", friend.getUid(), friend.getFriendUid());
            }
            String name = nonBlank(friend.getName());
            FriendResp friendResp = new FriendResp();
            friendResp.setUid(friend.getFriendUid());
            friendResp.setName(name == null ? "用户 " + friend.getFriendUid() : name);
            friendResp.setRemark(nonBlank(friend.getNickname()));
            friendResp.setDisplayName(friendResp.getRemark() == null ? friendResp.getName() : friendResp.getRemark());
            friendResp.setIndexLetter(NameSortKey.indexLetter(friend.getSortKey()));
            friendResp.setRoomId(friendRoomMap.get(friend.getFriendUid()));
            return friendResp;
        }).toList();
    }

    private static String nonBlank(String value) {
        return value == null || Normalizer.normalize(value, Normalizer.Form.NFKC).isBlank()
                ? null : value.strip();
    }


    public static UserApply buildFriendApply(Long uid, FriendApplyReq applyReq, UserApply lastApply) {
        List<ApplyChat> applyMsg = new ArrayList<>(Optional.ofNullable(lastApply.getMsg()).orElse(List.of()));
        applyMsg.add(new ApplyChat(uid, applyReq.getMessage()));
        UserApply userApply = new UserApply();
        userApply.setUid(uid);
        userApply.setMsg(applyMsg);
        userApply.setType(ApplyTypeEnum.ADD_FRIEND);
        userApply.setTargetId(applyReq.getTargetUid());
        userApply.setRequestSettings(buildApplySettings(applyReq));
        userApply.setStatus(ApplyStatusEnum.WAIT_APPROVE);
        userApply.setReadStatus(ApplyReadEnum.UNREAD);
        userApply.setSenderReadStatus(ApplyReadEnum.READ);
        userApply.setSenderDelete(YesOrNoEnum.NO);
        userApply.setReceiverDeletedStatus(YesOrNoEnum.NO);
        userApply.setVersion(0);
        return userApply;
    }

    /** 归一化私有设置，重复请求按字段语义比较而非 JSON 字符串顺序。 */
    public static FriendApplySettings buildApplySettings(FriendApplyReq request) {
        return new FriendApplySettings(
                request.getRemark() == null ? "" : request.getRemark().strip(),
                request.getTags().stream().map(String::strip).distinct().toList(),
                request.getPermission(), request.getHideMine(), request.getHideTheirs());
    }

    /** 历史申请没有快照时只兼容读取，不自动修改历史记录。 */
    public static FriendApplySettings readApplySettings(UserApply apply) {
        if (apply.getRequestSettings() == null) {
            return new FriendApplySettings("", List.of(), "all", false, false);
        }
        return apply.getRequestSettings();
    }

    /** 返回当前阅读者对应的另一方，申请人标识仍保留原始语义。 */
    public static Long friendApplyPeerUid(Long uid, UserApply apply) {
        return uid.equals(apply.getUid()) ? apply.getTargetId() : apply.getUid();
    }

    /** 只组装申请公开字段和既有名称，不附带头像或私有申请设置。 */
    public static List<FriendApplyResp> buildFriendApplyList(Long uid, List<UserApply> records,
                                                            Map<Long, User> usersInfo) {
        return records.stream().map(userApply -> {
            Long peerUid = friendApplyPeerUid(uid, userApply);
            User peer = usersInfo.get(peerUid);
            String name = peer == null ? null : nonBlank(peer.getName());
            FriendApplyResp friendApplyResp = new FriendApplyResp();
            friendApplyResp.setUid(userApply.getUid());
            friendApplyResp.setPeerUid(peerUid);
            friendApplyResp.setDirection(uid.equals(userApply.getUid()) ? "SENT" : "RECEIVED");
            friendApplyResp.setType(userApply.getType());
            friendApplyResp.setApplyId(userApply.getId());
            friendApplyResp.setMsg(userApply.getMsg());
            friendApplyResp.setStatus(userApply.getStatus());
            friendApplyResp.setName(name == null ? "用户 " + peerUid : name);
            return friendApplyResp;
        }).collect(Collectors.toList());
    }
}
