package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.user.domain.dto.FriendRemarkData;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 用户联系人表
 * </p>
 *
 * @author TGH
 * @since 2023-12-10
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "user_friend", autoResultMap = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFriend implements Serializable {

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
     * 好友uid
     */
    @TableField("friend_uid")
    private Long friendUid;

    /** uid 对 friendUid 的私有备注，不向其他成员广播。 */
    @TableField("nickname")
    private String nickname;

    @TableField("nickname_sort_key")
    private String nicknameSortKey;

    /** 私有备注扩展 JSON：标签、电话、描述及图片；兼容历史标签数组。 */
    @TableField(value = "tags", typeHandler = Jackson3TypeHandler.class)
    private FriendRemarkData tags;

    /**
     * 本人是否拉黑对方(0-正常,1-拉黑)，与删除状态独立
     */
    @TableField("black_status")
    private YesOrNoEnum blackStatus;

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

    @TableField("version")
    @Version
    private Integer version;
}
