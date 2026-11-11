package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 群聊房间表
 * </p>
 *
 * @author TGH
 * @since 2023-12-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "room_group", autoResultMap = true)
public class RoomGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群主id
     */
    @TableField(value = "uid")
    private Long uid;

    /**
     * 房间id
     */
    @TableField("room_id")
    private Long roomId;

    /**
     * 群名称
     */
    @TableField("name")
    private String name;

    /**
     * 额外信息（根据不同类型房间有不同存储的东西）
     */
    @TableField(value = "ext_json", typeHandler = Jackson3TypeHandler.class)
    private RoomGroupExtra extJson;

    /**
     * 逻辑删除(0-正常,1-删除)
     */
    @TableField("delete_status")
    private YesOrNoEnum deleteStatus;

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

    /**
     * 版本乐观锁
     */
    @TableField("version")
    @Version
    private Integer version;
}
