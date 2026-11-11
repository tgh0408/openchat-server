package com.openchat.chat.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.chat.dao.RoomGroupDao;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.domain.entity.RoomFriend;
import com.openchat.user.dao.UserDao;
import com.openchat.user.dao.UserFriendDao;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.entity.UserFriend;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.user.domain.enums.IdTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomAccessService {
    private final RoomDao roomDao;
    private final RoomGroupDao roomGroupDao;
    private final GroupMemberDao groupMemberDao;
    private final RoomFriendDao roomFriendDao;
    private final UserDao userDao;
    private final UserFriendDao userFriendDao;

    /** 查询本人方向未删除的关系；删除好友及私有备注不要求反向关系存在，也不受拉黑阻断。 */
    public UserFriend findFriendRelation(Long uid, Long targetUid) {
        if (!validFriendPair(uid, targetUid)) return null;
        UserFriend friend = userFriendDao.get(uid, targetUid);
        return activeRelation(friend) ? friend : null;
    }

    /** 查询本人方向未删除、未拉黑的通讯录好友。 */
    public UserFriend findAvailableFriend(Long uid, Long targetUid) {
        UserFriend friend = findFriendRelation(uid, targetUid);
        return availableRelation(friend) ? friend : null;
    }

    /** 判断本人通讯录中的有效好友，不将对方单向删除误当作本人通讯录删除。 */
    public boolean isMyFriend(Long uid, Long targetUid) {
        return findAvailableFriend(uid, targetUid) != null;
    }

    /** 创建群聊只校验双向有效好友及双方好友拉黑标记，不依赖单聊房间。 */
    public boolean isFriend(Long uid, Long targetUid) {
        return isMyFriend(uid, targetUid) && isMyFriend(targetUid, uid);
    }

    /** 好友拉黑只读取 user_friend，单条查询包含已删除关系，不依赖通讯录缓存。 */
    public boolean isMyFriendBlacklisted(Long uid, Long targetUid) {
        if (!validFriendPair(uid, targetUid)) return false;
        UserFriend friend = userFriendDao.getRelation(uid, targetUid);
        return friend != null && friend.getBlackStatus() == YesOrNoEnum.YES;
    }

    /** 判断双方关系是否均未删除；拉黑不等于删除，不据此重复建立关系。 */
    public boolean hasMutualFriendRelation(Long uid, Long targetUid) {
        return findFriendRelation(uid, targetUid) != null && findFriendRelation(targetUid, uid) != null;
    }

    /** 批量检查复用当前账号的关系快照，保持一次批量读取及原请求顺序由调用方组装。 */
    public Set<Long> friendUids(Long uid, List<Long> targetUids) {
        if (uid == null || uid <= 0) return Set.of();
        return userFriendDao.getByFriends(uid, targetUids).stream()
                .filter(this::availableRelation)
                .map(UserFriend::getFriendUid)
                .filter(friendUid -> validFriendPair(uid, friendUid))
                .collect(Collectors.toSet());
    }

    /** 按本人和单聊房间校验好友关系；删除不要求反向关系存在，也不受拉黑阻断。 */
    public UserFriend requireFriendRelation(Long uid, Long roomId) {
        if (uid == null || uid <= 0 || roomId == null || roomId <= 0) {
            throw new BusinessException("用户或房间参数无效");
        }
        if (requireRoom(roomId).getType() != RoomTypeEnum.FRIEND) {
            throw new BusinessException("当前房间不是单聊房间");
        }
        UserFriend friend = findFriendRelation(uid, requireFriendPeer(uid, roomId));
        if (friend == null) throw new BusinessException("好友关系不存在");
        return friend;
    }

    /** 资料读取要求本人方向的有效好友，不允许已删除或拉黑关系泄露私有资料。 */
    public UserFriend requireFriend(Long uid, Long targetUid) {
        UserFriend friend = findAvailableFriend(uid, targetUid);
        if (friend == null) throw new BusinessException("好友关系不存在或资料不可用");
        return friend;
    }

    /** 房间参与者与好友关系分离，历史、撤回和解除黑名单只需要真实参与者身份。 */
    public Long requireFriendPeer(Long uid, Long roomId) {
        if (uid == null || uid <= 0) throw new BusinessException("请先登录");
        RoomFriend friend = roomFriendDao.get(roomId,"你不在当前单聊房间中");
        if (!Objects.equals(friend.getUid1(), uid) && !Objects.equals(friend.getUid2(), uid)) {
            throw new BusinessException("你不在当前单聊房间中");
        }
        Long peer = friend.getFriend(uid);
        if (!validFriendPair(uid, peer)) throw new BusinessException("单聊好友信息无效");
        return peer;
    }

    /** 申请及审批共用好友校验；只检查权限，不负责创建关系或提供跨节点互斥。 */
    public void requireCanAddFriend(Long uid, Long targetUid) {
        if (!validFriendPair(uid, targetUid)) throw new BusinessException("好友对象无效");
        requireNoRelationBlacklist(uid, targetUid);
        if (hasMutualFriendRelation(uid, targetUid)) throw new BusinessException("你们已经是好友了");
    }

    private boolean validFriendPair(Long uid, Long targetUid) {
        return uid != null && uid > 0 && targetUid != null && targetUid > 0 && !uid.equals(targetUid);
    }

    private boolean activeRelation(UserFriend friend) {
        return friend != null;
    }

    private boolean availableRelation(UserFriend friend) {
        return activeRelation(friend) && friend.getBlackStatus() != YesOrNoEnum.YES;
    }

    /** 双向拉黑都阻止新申请与发送，解除操作只修改本人方向。 */
    private void requireNoRelationBlacklist(Long uid, Long targetUid) {
        if (isMyFriendBlacklisted(uid, targetUid)) {
            throw new BusinessException("你已将对方拉黑，请解除黑名单后再操作");
        }
        if (isMyFriendBlacklisted(targetUid, uid)) {
            throw new BusinessException("对方已将你拉黑了");
        }
    }

    /** 单聊发送要求接收方仍保留发送方为好友；好友拉黑不扩展为群禁言。 */
    public Room requireCanSend(Long uid, Long roomId) {
        Room room = requireCurrentMember(uid, roomId);
        if (room.getType() == RoomTypeEnum.FILE_ASSISTANT) return room;
        if (room.getType() == RoomTypeEnum.FRIEND) {
            Long peer = requireFriendPeer(uid, roomId);
            // 本人删除对方不阻止向对方发送；对方删除本人才拒绝本次发送。
            if (findFriendRelation(peer, uid) == null) {
                throw new BusinessException("好友关系已解除，请重新添加好友");
            }
            if (isMyFriendBlacklisted(peer, uid)) {
                throw new BusinessException("消息已发出，但被对方拒收了。");
            }
            if (isMyFriendBlacklisted(uid, peer)) {
                throw new BusinessException("对方已在黑名单中，无法发送消息");
            }
            return room;
        }
        return room;
    }


    public Room requireRoom(Long roomId) {
        return roomDao.get(roomId,"房间不存在");
    }

    /** 下载校验当前房间归属及账号状态；对方拉黑本人时不再签发单聊附件凭证。 */
    public Room requireCanDownload(Long uid, Long roomId) {
        User user = userDao.get(IdTypeEnum.ID, uid);
        if (user == null || user.getStatus() != YesOrNoEnum.NO) {
            throw new BusinessException("账号状态异常，无法下载附件");
        }
        Room room = requireCurrentMember(uid, roomId);
        if (room.getType() == RoomTypeEnum.FRIEND
                && isMyFriendBlacklisted(requireFriendPeer(uid, roomId), uid)) {
            throw new BusinessException("对方已将你拉黑，无法下载附件");
        }
        return room;
    }

    public Room requireCurrentMember(Long uid, Long roomId) {
        if (uid == null || uid <= 0) {
            throw new BusinessException("请先登录");
        }
        Room room = roomDao.get(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (room.getType() == RoomTypeEnum.GROUP) {
            RoomGroup group = roomGroupDao.get(roomId);
            if (group == null || group.getDeleteStatus() != YesOrNoEnum.NO) {
                throw new BusinessException("群已解散或你不在当前群中");
            }
            GroupMember member = groupMemberDao.getMember(roomId, uid);
            if (member == null || member.getDeleteStatus() != YesOrNoEnum.NO) {
                throw new BusinessException("群已解散或你不在当前群中");
            }
        } else if (room.getType() == RoomTypeEnum.FRIEND) {
            requireFriendPeer(uid, roomId);
        } else if (room.getType() == RoomTypeEnum.FILE_ASSISTANT) {
            // 即使已知真实房间号，也只能访问自己的文件助手，不能靠 contact 记录授权。
            if (!Objects.equals(room.getOwnerUid(), uid)) {
                throw new BusinessException("无权访问该文件助手");
            }
        } else {
            throw new BusinessException("无效的房间类型");
        }
        return room;
    }

}
