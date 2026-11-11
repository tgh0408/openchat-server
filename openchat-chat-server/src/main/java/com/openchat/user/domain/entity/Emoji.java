package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author TGH
 * @since 2024-11-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emoji")
public class Emoji implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 表情地址
     */
    @TableField("url")
    private String url;

    /**
     * 表情描述
     */
    @TableField("description")
    private String description;

    /**
     * 加密
     */
    @TableField("sha_256")
    private String sha256;

    /**
     * 首次创建id
     */
    @TableField("create_id")
    private Long createId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 拓展信息
     */
    @TableField("extra")
    private Object extra;


}
