package com.openchat.chat.domain.entity.msg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 单个新成员的公开昵称快照，前端按字段高亮，不解析或执行正文中的标记。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitationMsgDTO implements Serializable {
    private String kind;
    private String inviterName;
    private String memberName;
}
