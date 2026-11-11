package com.openchat.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 消息表
 * </p>
 *
 * @author TGH
 * @since 2023-12-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "message", autoResultMap = true)
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 会话表id
     */
    @TableField("room_id")
    private Long roomId;

    /**
     * 消息发送者uid
     */
    @TableField("from_uid")
    private Long fromUid;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 引用的服务端消息 ID
     */
    @TableField("reply_msg_id")
    private Long replyMsgId;

    /**
     * 前端客户端消息 id（用于发送确认/去重）
     */
    @TableField("client_msg_id")
    private String clientMsgId;

    /**
     * 消息状态 0正常 1删除
     */
    @TableField("status")
    private YesOrNoEnum status;

    /**
     * 落库事务快照中两条消息之间的正常状态消息数，不包含两端；无引用时为空
     */
    @TableField("gap_count")
    private Long gapCount;

    /**
     * 消息类型 1正常文本 2.撤回消息 3.大文件内容占位 4.图片 5.视频 6.文件 7.音频 8.语音 9.表情 10 系统消息 11 RTC通话
     */
    @TableField("type")
    private MessageTypeEnum type;

    /**
     * 扩展信息
     */
    @TableField(value = "extra", typeHandler = Jackson3TypeHandler.class)
    private MessageExtra extra;

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
