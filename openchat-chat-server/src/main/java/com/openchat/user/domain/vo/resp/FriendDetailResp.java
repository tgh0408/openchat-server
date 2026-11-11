package com.openchat.user.domain.vo.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/** 仅向当前好友关系所有者返回的详情。 */
@Data
public class FriendDetailResp {
    private Long uid;
    private Long roomId;
    @Schema(description = "当前用户是否拉黑对方")
    private boolean blacklisted;
    private String name;
    private String remark;
    private FriendRemarkResp remarkSettings;
    private String displayName;
    private String avatar;
    @Schema(description = "性别：1 男，0 女，其他或空值表示未设置")
    private Integer sex;
    private String personalitySignature;
    @Schema(description = "当前好友关系记录的创建时间；不代表删除后重新添加的时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Date addedAt;
}
