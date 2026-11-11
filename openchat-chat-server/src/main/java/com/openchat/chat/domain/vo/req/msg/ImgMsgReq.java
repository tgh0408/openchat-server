package com.openchat.chat.domain.vo.req.msg;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImgMsgReq {
    @NotBlank(message = "图片路径不能为空")
    @Size(max = 2048, message = "图片文件路径太长了")
    @Schema(description = "已上传图片的公开地址或对象路径，不包含临时上传签名")
    private String uploadAddress;

    @NotBlank(message = "图片文件路径不能为空")
    @Size(max = 2048, message = "图片文件路径太长了")
    @Schema(description = "图片下载地址")
    private String content;

    @NotBlank(message = "图片名字不能为空")
    @Size(max = 255, message = "图片名字太长了")
    @Schema(description = "图片名称")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1,message = "文件大小不能为0")
    @Max(value = 52428800, message = "图片不能超过 50 MiB")
    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "回复的消息id,如果没有别传就好")
    private Long replyMsgId;

    @Schema(description = "艾特的uid")
    @Size(max = 10, message = "一次最多@10个人")
    private List<Long> atUidList;
}
