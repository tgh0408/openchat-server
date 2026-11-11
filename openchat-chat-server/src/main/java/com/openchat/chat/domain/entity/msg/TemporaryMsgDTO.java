package com.openchat.chat.domain.entity.msg;

import com.openchat.chat.domain.enums.MessageTypeEnum;
import lombok.*;
import java.io.Serializable;

/** 上传占位的公共元数据；视频附加字段可缺省，状态只能由服务端推进。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporaryMsgDTO implements Serializable {
    private MessageTypeEnum targetType;
    private String fileName;
    private Long size;
    private Long duration;
    private Integer thumbWidth;
    private Integer thumbHeight;
    private boolean cancelled;
}
