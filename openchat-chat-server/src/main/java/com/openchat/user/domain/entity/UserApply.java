package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.user.domain.dto.ApplyChat;
import com.openchat.user.domain.dto.FriendApplySettings;
import com.openchat.user.domain.enums.ApplyReadEnum;
import com.openchat.user.domain.enums.ApplyStatusEnum;
import com.openchat.user.domain.enums.ApplyTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 用户申请表
 * </p>
 *
 * @author TGH
 * @since 2023-12-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "user_apply", autoResultMap = true)
public class UserApply implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 申请人uid
     */
    @TableField("uid")
    private Long uid;

    /**
     * 申请类型 1加好友 2加群
     */
    @TableField("type")
    private ApplyTypeEnum type;

    /**
     * 接收人uid
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 申请信息
     */
    @TableField(value = "msg", typeHandler =  Jackson3TypeHandler.class)
    private List<ApplyChat> msg;

    /** 私有申请设置 JSON，不向接收方的申请列表或通知返回。 */
    @TableField(value = "request_settings", typeHandler = Jackson3TypeHandler.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private FriendApplySettings requestSettings;

    /**
     * 申请状态 1待审批 2同意 3已拒绝；黑名单关系由 user_friend 判断。
     */
    @TableField("status")
    private ApplyStatusEnum status;

    /**
     * 接收方阅读状态 1未读 2已读
     */
    @TableField("read_status")
    private ApplyReadEnum readStatus;

    /** 申请发送方对回复的阅读状态，独立于接收方。 */
    @TableField("sender_read_status")
    private ApplyReadEnum senderReadStatus;

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
     * 发送者删除标记
     */
    @TableField("sender_delete")
    private YesOrNoEnum senderDelete;

    /**
     * 接收者删除标记
     */
    @TableField("receiver_deleted")
    private YesOrNoEnum receiverDeletedStatus;

    /**
     * 版本号
     */
    @Version
    @TableField("version")
    private Integer version;
}
