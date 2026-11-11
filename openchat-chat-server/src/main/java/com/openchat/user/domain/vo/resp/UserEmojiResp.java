package com.openchat.user.domain.vo.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Description: 表情包反参
 * Author: 
 * Date: 2023-07-09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEmojiResp {
    /**
     * id
     */
    @Schema(description = "id")
    private Long id;

    /**
     * 表情地址
     */
    @Schema(description = "表情url")
    private String expressionUrl;

}
