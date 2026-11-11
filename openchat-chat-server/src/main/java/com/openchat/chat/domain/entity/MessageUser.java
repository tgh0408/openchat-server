package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * 消息表
 * </p>
 *
 * @author TGH
 * @since 2024-08-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "message_user", autoResultMap = true)
@NoArgsConstructor
@AllArgsConstructor
public class MessageUser extends Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * uid 谁的信箱
     */
    @TableField("uid")
    private Long uid;



}
