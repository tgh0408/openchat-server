package com.openchat.user.domain.dto;

import lombok.Data;
import java.util.Date;

/** 好友关系与公开资料的组装投影，不包含账号凭据或 IP。 */
@Data
public class FriendDetailRow {
    private Long uid;
    private String name;
    private String remark;
    private String avatar;
    private Integer sex;
    private String personalitySignature;
    private Date addedAt;
}
