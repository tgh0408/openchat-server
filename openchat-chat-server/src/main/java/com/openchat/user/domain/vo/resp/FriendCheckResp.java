package com.openchat.user.domain.vo.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * Description: 好友校验
 * Author: <a  > TGH</a>
 * Date: 2023-03-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendCheckResp {

    @Schema(description = "校验结果")
    private List<FriendCheck> checkedList;

    @Data
    public static class FriendCheck {
        private Long uid;
        private Boolean isFriend;
    }

}
