package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendApplyReq {
    @NotNull
    @Positive
    @JsonAlias("friendId")
    @Schema(description = "申请目标ID")
    private Long targetUid;

    @NotBlank
    @Size(max = 200)
    @JsonAlias({"msg", "Msg"})
    @Schema(description = "申请好友时的描述")
    private String message;

    @Size(max = 64)
    @Schema(description = "申请人对目标用户的私有备注")
    private String remark = "";

    @NotNull
    @Size(max = 10)
    @Schema(description = "申请人私有标签，每项最多32字")
    private List<@NotBlank @Size(max = 32) String> tags = new ArrayList<>();

    @NotNull
    @Pattern(regexp = "all|chat")
    @Schema(description = "预留：all或chat，当前不参与朋友圈授权")
    private String permission = "all";

    @NotNull
    @Schema(description = "预留：不让对方看朋友圈，当前不生效")
    private Boolean hideMine = false;

    @NotNull
    @Schema(description = "预留：不看对方朋友圈，当前不生效")
    private Boolean hideTheirs = false;

}
