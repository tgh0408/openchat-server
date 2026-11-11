package com.openchat.chat.domain.vo.req;

import com.openchat.common.domain.enums.YesOrNoEnum;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 当前用户的会话免打扰设置，不接受客户端指定操作者。 */
@Data
public class ContactNotDisturbReq {
    @NotNull
    @Min(1)
    private Long roomId;

    @NotNull
    private YesOrNoEnum status;
}
