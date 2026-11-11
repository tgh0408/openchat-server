package com.openchat.user.domain.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupMemberInfo {
    private Long uid;
    private String username;
    /** 成员在当前群的昵称，未设置时为空。 */
    private String nickname;
    private Integer role;
}
