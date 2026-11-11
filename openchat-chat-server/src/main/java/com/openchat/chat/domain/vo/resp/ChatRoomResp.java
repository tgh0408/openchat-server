package com.openchat.chat.domain.vo.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Description: 群成员列表的成员信息
 * Author: 
 * Date: 2023-03-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatRoomResp {
    @Schema(description = "房间id")
    private Long roomId;
    @Schema(description = "单聊对方用户id；群聊和文件助手为空")
    private Long participantId;
    @Schema(description = "房间类型 1群聊 2单聊 3文件助手")
    private RoomTypeEnum type;
    @Schema(description = "是否置顶 0否 1是")
    private YesOrNoEnum isTop;
    @Schema(description = "是否免打扰 0否 1是")
    private YesOrNoEnum notDisturb;
    @Schema(description = "最新消息")
    private String text;
    @Schema(description = "会话名称")
    private String name;
    @Schema(description = "群头像预览用户 UID 列表")
    private List<Long> avatarUidList;
    @Schema(description = "房间最后活跃时间(用来排序)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date activeTime;
    @Schema(description = "未读数")
    private Integer unreadCount;
    @Schema(description = "本次未读统计的房间消息上界")
    private Long lastMsgId;
    @Schema(description = "本次未读统计的已读位置，含历史删除边界")
    private Long lastReadMsgId;
    @Schema(description = "当前用户是否可以管理本群消息；仅详情返回，不作为客户端授权凭据")
    private boolean canRecallMembers;
    @Schema(description = "当前用户已退出群聊，只能读取本地消息")
    private boolean groupRemoved;
}
