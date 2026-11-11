package com.openchat.chat.domain.entity.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * Description: 语音消息入参
 * Author: 
 * Date: 2023-06-04
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileMsgDTO extends BaseFileDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文件名（带后缀）")
    @NotBlank
    private String fileName;

    @NotBlank
    @Schema(description = "文件上传地址")
    private String upLoadPath;

}
