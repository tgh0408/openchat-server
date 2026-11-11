package com.openchat.chat.domain.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 仅公开撤回者身份、用户名和本群昵称，不返回私有备注、原文或资源绑定。 */
@Data
@AllArgsConstructor
public class RecallMsgResp {
    // MQ 使用独立转换器，Object 正文还原后不能再依赖 Long 的全局序列化配置。
    private Long recallUid;
    private String recallName;
    private String recallGroupNickname;
}
