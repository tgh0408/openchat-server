package com.openchat.chat.domain.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class RoomGroupExtra implements Serializable {
    @Schema(description = "群公告")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Notice notice;
    @Schema(description = "二维码")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<QRCode> qrCode;

    @Data
    public static final class Notice implements Serializable {
        @Schema(description = "公告内容")
        private String description;
        @Schema(description = "公告时间")
        private Date createTime;
        @Schema(description = "写入用户")
        private Long uid;
    }

    @Data
    public static final class QRCode implements Serializable {
        @Schema(description = "二维码路径")
        private String url;
        @Schema(description = "二维码生成时间")
        private Date createTime;
        @Schema(description = "二维码过期时间")
        private Date expireTime;
        @Schema(description = "二维码密钥")
        private String keyword;
    }
}
