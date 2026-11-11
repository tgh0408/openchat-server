package com.openchat.user.domain.vo.req;

import com.openchat.user.domain.enums.IdTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TargetInfoReq {
    @NotBlank
    private String searchText;
    private IdTypeEnum code;
    @Positive
    private Long roomId;
}
