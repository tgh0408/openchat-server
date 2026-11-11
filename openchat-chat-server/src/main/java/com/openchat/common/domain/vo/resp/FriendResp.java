package com.openchat.common.domain.vo.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendResp {

    @Schema(description = "好友uid")
    private Long uid;

    @Schema(description = "正常单聊房间id，缺失或停用时为null；不代表发送权限")
    private Long roomId;

    @Schema(description = "好友公开名称，资料缺失或名称为空时回退为用户 UID")
    private String name;

    @Schema(description = "当前用户的私有好友备注，未设置时为null")
    private String remark;

    @Schema(description = "显示名称，优先好友备注，其次公开名称")
    private String displayName;

    @Schema(description = "通讯录分组：A-Z 或 #，# 排最后")
    private String indexLetter;
}
