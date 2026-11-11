package com.openchat.chat.domain.vo.req;

import com.openchat.common.domain.vo.req.CursorPageBaseReq;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

/**
 * Description: 消息列表请求
 * Author: 
 * Date: 2023-03-29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessagePageReq extends CursorPageBaseReq {
    @NotNull
    @Schema(description = "真实会话id，文件助手从会话列表获取")
    private Long roomId;
}
