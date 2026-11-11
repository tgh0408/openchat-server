package com.openchat.user.domain.vo.req;

import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Data
public class TopOrUnTopReq {
    @NotNull
    @Min(1)
    private Long roomId;

    @NotNull
    private YesOrNoEnum status;
}
