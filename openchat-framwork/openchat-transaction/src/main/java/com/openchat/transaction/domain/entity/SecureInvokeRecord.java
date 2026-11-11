package com.openchat.transaction.domain.entity;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.openchat.transaction.domain.dto.SecureInvokeDTO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Description:
 * Author: 
 * Date: 2023-08-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "secure_invoke_record", autoResultMap = true)
public class SecureInvokeRecord {
    public final static byte STATUS_WAIT = 1;
    public final static byte STATUS_FAIL = 2;
    public final static byte STATUS_RUNNING = 3;
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 请求快照参数json
     */
    @TableField(value = "secure_invoke_json", typeHandler = Jackson3TypeHandler.class)
    private SecureInvokeDTO secureInvokeDTO;
    /**
     * 状态 1待执行 2重试耗尽 3执行中
     */
    @TableField("status")
    @Builder.Default
    private byte status = SecureInvokeRecord.STATUS_WAIT;
    /**
     * 下一次重试的时间
     */
    @TableField("next_retry_time")
    @Builder.Default
    private Date nextRetryTime = new Date();
    /**
     * 已经重试的次数
     */
    @TableField("retry_times")
    @Builder.Default
    private Integer retryTimes = 0;
    @TableField("max_retry_times")
    private Integer maxRetryTimes;
    @TableField("fail_reason")
    private String failReason;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;

}
