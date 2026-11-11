package com.openchat.chat.domain.entity.msg;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * Description: 视频消息入参
 * Author: 
 * Date: 2023-06-04
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class VideoMsgDTO extends BaseFileDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "视频时长，毫秒")
    private Long duration;

    @Schema(description = "缩略图宽度（像素）")
    @NotNull
    private Integer thumbWidth;

    @Schema(description = "缩略图高度（像素）")
    @NotNull
    private Integer thumbHeight;

    @Schema(description = "缩略图大小（字节）")
    @NotNull
    private Long thumbSize;

    @Schema(description = "缩略图下载地址")
    @NotBlank
    private String thumbUrl;

}
