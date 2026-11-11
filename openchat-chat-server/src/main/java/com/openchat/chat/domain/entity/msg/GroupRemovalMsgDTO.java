package com.openchat.chat.domain.entity.msg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;

/** 被移出群聊的成员快照，前端按 memberUid 判断是否展示“你被移出群聊”。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupRemovalMsgDTO implements Serializable {
    private String kind;
    private String operatorName;
    // HTTP/SSE 使用 Jackson 3，RocketMQ 使用 Jackson 2；共享格式注解避免中转变回数字。
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long memberUid;
    private String memberName;
}
