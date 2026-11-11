package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.openchat.chat.domain.enums.ContactStateEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 会话列表
 * </p>
 *
 * @author TGH
 * @since 2023-12-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contact")
public class Contact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * uid
     */
    @TableField("uid")
    private Long uid;

    /**
     * 房间id
     */
    @TableField("room_id")
    private Long roomId;

    /**
     * 阅读到的时间
     */
    @TableField("read_time")
    private Date readTime;

    /**
     * 会话内消息最后更新的时间(只有普通会话需要维护，全员会话不需要维护)
     */
    @TableField("active_time")
    private Date activeTime;

    /**
     * 是否置顶
     */
    @TableField("is_top")
    private YesOrNoEnum isTop;

    /**
     * 是否免打扰
     */
    @TableField("not_disturb")
    private YesOrNoEnum notDisturb;

    /**
     * 已经删除的历史记录位置
     */
    @TableField("last_delete_id")
    private Long lastDeleteId;

    /**
     * 用户最新阅读的位置，用于计算未读数量
     */
    @TableField("last_read_msg_id")
    private Long lastReadMsgId;

    /** 会话可隐藏后恢复，由 DAO 显式控制可见状态，不使用自动逻辑删除过滤。 */
    @TableField("last_hidden_msg_id")
    private Long lastHiddenMsgId;

    @TableField("contact_state")
    private ContactStateEnum contactState;

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


    @TableField("version")
    @Version
    private Long version;
}
