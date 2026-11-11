package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 消息标记表
 * </p>
 *
 * @author TGH
 * @since 2023-12-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("message_mark")
public class MessageMark implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息表id
     */
    @TableField("msg_id")
    private Long msgId;

    /**
     * 标记人uid
     */
    @TableField("uid")
    private Long uid;

    /**
     * 标记类型 1
     */
    @TableField("type")
    private Integer type;

    /**
     * 消息状态 0正常 1取消
     */
    @TableField("status")
    private YesOrNoEnum status;

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

    @Version
    @TableField("version")
    private Integer version;
}
