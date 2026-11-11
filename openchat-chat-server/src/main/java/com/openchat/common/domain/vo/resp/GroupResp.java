package com.openchat.common.domain.vo.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupResp {
    @Schema(description = "群主id")
    private Long groupId;

    @Schema(description = "房间id")
    private Long roomId;

    @Schema(description = "群名称，空字符串表示由前端按前九位成员名称生成默认群名")
    private String name;

    @Schema(description = "群成员数量")
    private Integer memberCount;

    @Schema(description = "群头像预览用户 UID 列表，最多前九位")
    private List<Long> avatarUidList;

    @Schema(description = "群公告记录")
    private Notice notice;

    @Schema(description = "群二维码记录")
    private List<QRCode> qrCode;

    @Schema(description = "是否是我创建的")
    private Boolean create;

    @Schema(description = "是否有管理员权限")
    private Boolean root;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notice {
        @Schema(description = "公告内容")
        private String description;

        @Schema(description = "公告写入时间")
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                timezone = "UTC")
        private Date createTime;

        @Schema(description = "公告写入用户 UID")
        private Long uid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QRCode {
        @Schema(description = "二维码图片地址")
        private String url;

        @Schema(description = "二维码生成时间")
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                timezone = "UTC")
        private Date createTime;

        @Schema(description = "二维码过期时间")
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                timezone = "UTC")
        private Date expireTime;

        @Schema(description = "二维码密钥")
        private String keyword;
    }
}
