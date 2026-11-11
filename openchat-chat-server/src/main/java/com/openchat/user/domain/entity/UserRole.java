package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.openchat.user.domain.enums.SystemRoleEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 用户角色关系表
 * </p>
 *
 * @author TGH
 * @since 2023-11-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_role")
public class UserRole implements Serializable {

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
     * 角色id
     */
    @TableField("role_id")
    private SystemRoleEnum roleId;

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
