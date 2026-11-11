package com.openchat.user.domain.vo.resp;

import com.openchat.user.domain.dto.ApplyChat;
import com.openchat.user.domain.enums.ApplyStatusEnum;
import com.openchat.user.domain.enums.ApplyTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendApplyResp {
    @Schema(description = "申请id")
    private Long applyId;

    @Schema(description = "申请人uid")
    private Long uid;

    @Schema(description = "当前登录用户视角的对方uid；收到时为申请人，发出时为接收人")
    private Long peerUid;

    @Schema(description = "相对当前登录用户的方向：RECEIVED收到，SENT发出")
    private String direction;

    @Schema(description = "对方名称；头像及其他资料由客户端按peerUid查询")
    private String name;

    @Schema(description = "申请类型 1加好友 2加群")
    private ApplyTypeEnum type;

    @Schema(description = "申请信息")
    private List<ApplyChat> msg;

    @Schema(description = "申请状态 1待审批 2同意 3拒绝")
    private ApplyStatusEnum status;

    @Schema(description = "处理状态名称")
    private Integer statusName;
}
