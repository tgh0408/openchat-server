package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.openchat.chat.domain.enums.GroupRoleEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 群成员表
 * </p>
 *
 * @author TGH
 * @since 2023-12-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("group_member")
public class GroupMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 房间id
     */
    @TableField("room_id")
    private Long roomId;

    /**
     * 成员uid
     */
    @TableField("uid")
    private Long uid;

    /** 成员在本群的昵称，空值回退用户名。 */
    @TableField("nickname")
    private String nickname;

    /**
     * 成员角色 1群主 2管理员 3普通成员
     */
    @TableField("role")
    private GroupRoleEnum role;

    /** 退出关系可恢复；普通实体更新不能修改状态，由 DAO 的条件更新负责。 */
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
