package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openchat.common.exception.BusinessException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 单聊房间表
 * </p>
 *
 * @author TGH
 * @since 2023-12-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("room_friend")
@NoArgsConstructor
// 只兼容旧 Redis 载荷，不再映射或使用该字段。
@JsonIgnoreProperties("status")
public class RoomFriend implements Serializable {

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
     * uid1（更小的uid）
     */
    @TableField("uid1")
    private Long uid1;
    /**
     * uid2（更大的uid）
     */
    @TableField("uid2")
    private Long uid2;
    /**
     * 房间key由两个uid拼接，先做排序uid1_uid2
     */
    @TableField("room_key")
    private String roomKey;
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

    public Long getFriend(Long uid) {
        if (uid.equals(this.uid1)) {
            return this.uid2;
        } else if (uid.equals(this.uid2)) {
            return this.uid1;
        }
        throw new BusinessException("获取好友信息失败");
    }
}
