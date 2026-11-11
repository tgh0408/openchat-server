package com.openchat.user.service.adapter;

import com.openchat.user.domain.dto.FriendRemarkData;
import com.openchat.user.domain.entity.UserFriend;
import com.openchat.user.domain.vo.resp.FriendRemarkResp;

import java.util.List;

/** 从既有关系快照解码私有扩展字段，不引入数据库或缓存旁路读取。 */
public final class FriendRemarkAdapter {
    private FriendRemarkAdapter() {}

    public static FriendRemarkData read(UserFriend friend) {
        FriendRemarkData data = friend.getTags();
        if (data == null) return new FriendRemarkData(List.of(), List.of(), "", List.of());
        return new FriendRemarkData(
                data.tags() == null ? List.of() : data.tags(),
                data.phones() == null ? List.of() : data.phones(),
                data.description() == null ? "" : data.description(),
                data.images() == null ? List.of() : data.images());
    }

    public static FriendRemarkResp snapshot(UserFriend friend) {
        FriendRemarkData data = read(friend);
        return new FriendRemarkResp(friend.getVersion(), friend.getNickname(),
                data.tags(), data.phones(), data.description());
    }

    /** 审批只修改标签，不意外覆盖原关系已保存的电话和描述。 */
    public static FriendRemarkData withTags(UserFriend friend, List<String> tags) {
        FriendRemarkData data = read(friend);
        return new FriendRemarkData(tags, data.phones(), data.description(), data.images());
    }
}
