package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * 敏感词库
 * </p>
 *
 * @author TGH
 * @since 2024-08-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sensitive_word")
public class SensitiveWord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 敏感词
     */
    @TableId(value = "word",type = IdType.NONE)
    private String word;


}
