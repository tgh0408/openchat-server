package com.openchat.user.domain.vo.resp;

import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.enums.AddUserFunction;
import com.openchat.user.domain.enums.GenderEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserInfoResp {
    @Schema(description = "uid 1为群聊 2为用户")
    private RoomTypeEnum type = RoomTypeEnum.FRIEND;
    @Schema(description = "uid")
    private Long uid;
    @Schema(description = "用户名称")
    private String name;
    @Schema(description = "当前登录用户对目标用户的好友备注，仅本人可见")
    private String remark;
    @Schema(description = "本次查询的房间，未指定时为空")
    private Long roomId;
    @Schema(description = "目标用户在本群的昵称，空值回退用户名")
    private String groupNickname;
    @Schema(description = "用户头像公开地址")
    private String avatar;
    @Schema(description = "用户性别")
    private GenderEnum sex;
    @Schema(description = "加我时候的权限 1需要通过申请 2直接添加")
    private AddUserFunction addFunction;
    @Schema(description = "个性签名")
    private String personalitySignature;

    public UserInfoResp(User user) {
        this.uid = user.getId();
        this.name = user.getName();
        this.avatar = user.getAvatar();
        this.sex = user.getSex();
        this.addFunction = user.getAddFunction();
        this.personalitySignature = user.getPersonalitySignature();
    }
}
