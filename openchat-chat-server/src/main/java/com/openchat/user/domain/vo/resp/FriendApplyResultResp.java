package com.openchat.user.domain.vo.resp;

/**
 * 直接恢复好友的提交结果，不携带头像、名称或其他用户资料。
 */
public record FriendApplyResultResp(String status, Long uid, Long roomId, String indexLetter) {
}
