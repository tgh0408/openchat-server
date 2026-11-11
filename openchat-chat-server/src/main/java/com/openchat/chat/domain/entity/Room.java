package com.openchat.chat.domain.entity;

import com.openchat.chat.domain.enums.HotFlagEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 房间表
 * </p>
 *
 * @author TGH
 * @since 2023-12-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("room")
public class Room implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 房间类型 1群聊 2单聊 3文件助手
     */
    @TableField("type")
    private RoomTypeEnum type;

    /** 个人会话所属用户；普通群聊、单聊保持为空。 */
    @TableField("owner_uid")
    private Long ownerUid;

    /**
     * 是否全员展示 0否 1是
     */
    @TableField("hot_flag")
    private HotFlagEnum hotFlag;

    /**
     * 群最后消息的更新时间（热点群不需要写扩散，只更新这里）
     */
    @TableField("active_time")
    private Date activeTime;

    /**
     * 会话中的最后一条消息id
     */
    @TableField("last_msg_id")
    private Long lastMsgId;

    /**
     * 额外信息（根据不同类型房间有不同存储的东西）
     */
    @TableField("ext_json")
    private String extJson;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private Date updateTime;


    @JsonIgnore
    public boolean isHotRoom() {
        return this.hotFlag == HotFlagEnum.YES;
    }
}
