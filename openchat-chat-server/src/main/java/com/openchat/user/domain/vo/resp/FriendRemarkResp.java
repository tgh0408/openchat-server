package com.openchat.user.domain.vo.resp;

import java.util.List;

/** 当前关系所有者的可编辑快照，版本用于拒绝旧草稿覆盖。 */
public record FriendRemarkResp(Integer version, String remark, List<String> tags,
                               List<String> phones, String description) {}
