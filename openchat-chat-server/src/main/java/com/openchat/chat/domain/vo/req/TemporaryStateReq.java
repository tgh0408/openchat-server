package com.openchat.chat.domain.vo.req;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.List;

/** 重连时分批补查当前会话已经缓存的消息，普通状态与上传占位查询共用。 */
@Data
public class TemporaryStateReq {
    @NotNull @Min(1)
    private Long roomId;
    @NotEmpty @Size(max = 50)
    private List<@NotNull @Min(1) Long> msgIds;
}
