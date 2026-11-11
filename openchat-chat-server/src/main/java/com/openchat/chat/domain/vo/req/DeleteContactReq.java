package com.openchat.chat.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteContactReq {

    @NotNull
    @Schema(description = "会话id")
    private Long roomId;

    @PositiveOrZero
    @Schema(description = "本次清空观测到的最大消息ID，重试必须保持不变；缺省沿用在线清空")
    private Long lastDeleteId;

}
