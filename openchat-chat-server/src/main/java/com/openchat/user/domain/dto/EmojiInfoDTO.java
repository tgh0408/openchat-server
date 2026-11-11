package com.openchat.user.domain.dto;

import lombok.Data;

@Data
public class EmojiInfoDTO {
    private Long imgId;
    private String url;
    private String sha256;
    /** 按用户收藏关系返回，Long 沿用全局字符串序列化。 */
    private Long sort;
}
