package com.openchat.chat.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.openchat.chat.domain.enums.HotFlagEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Description: 房间详情
 * Author: 
 * Date: 2023-07-22
 */
@Data
public class RoomBaseInfo {
    @Schema(description = "房间id")
    private Long roomId;
    @Schema(description = "当前阅读者的单聊对方用户id")
    private Long participantId;
    @Schema(description = "会话名称")
    private String name;
    @Schema(description = "群头像预览用户 UID 列表")
    private List<Long> avatarUidList;
    /**
     * 房间类型 1群聊 2单聊 3 群助手
     */
    private RoomTypeEnum type;

    /**
     * 是否全员展示 0否 1是
     */
    private HotFlagEnum hotFlag;

    /**
     * 群最后消息的更新时间
     */
    @TableField("active_time")
    private Date activeTime;

    /**
     * 最后一条消息id
     */
    @TableField("last_msg_id")
    private Long lastMsgId;
}
