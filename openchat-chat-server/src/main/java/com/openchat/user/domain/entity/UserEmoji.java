package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 用户表情包
 * </p>
 *
 * @author TGH
 * @since 2024-01-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_emoji")
public class UserEmoji implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户表ID
     */
    @TableField("uid")
    private Long uid;

    /**
     * 表情地址
     */
    @TableField("emoji_id")
    private Long emojiId;

    /** 用户内排序，值越小越靠前。 */
    @TableField("sort")
    private Long sort;

    /**
     * 收藏状态(0-正常,1-删除)，DAO 显式过滤，恢复及排序分配可读取删除记录。
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

    @Version
    @TableField("version")
    private Integer version;

}
