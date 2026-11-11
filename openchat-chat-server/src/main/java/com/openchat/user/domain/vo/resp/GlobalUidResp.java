package com.openchat.user.domain.vo.resp;

import lombok.Builder;
import lombok.Data;

/**
 * 当前登录会话校验结果。
 */
@Data
@Builder
public class GlobalUidResp {
    private Long uid;
    private String clientSqlPassword;
}
