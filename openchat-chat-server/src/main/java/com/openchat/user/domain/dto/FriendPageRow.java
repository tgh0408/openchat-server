package com.openchat.user.domain.dto;

import lombok.Data;

/** 同一次查询中的关系、展示资料及有效排序键，不作为外部响应直接返回。 */
@Data
public class FriendPageRow {
    private Long id;
    private Long uid;
    private Long friendUid;
    private String nickname;
    private Long profileUid;
    private String name;
    private String sortKey;
}
