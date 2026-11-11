package com.openchat.chat.domain.entity.msg;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * @description:    文件基类
 * @date: 2023/10/07 下午 2:47
 */


@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseFileDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Schema(description = "大小（字节）")
    @NotNull
    private Long size;

    @Schema(description = "文件名称")
    private String fileName;
}
