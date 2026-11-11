package com.openchat.user.domain.vo.req;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一次 JSON 请求完整替换本人方向的文本备注。 */
@Data
public class FriendRemarkReq {
    @NotNull @Positive
    private Long targetUid;
    @NotNull @Positive
    private Long roomId;
    @NotNull @Min(0) @Max(Integer.MAX_VALUE - 1)
    private Integer version;
    @NotNull @Size(max = 64)
    private String remark = "";
    @NotNull @Size(max = 10)
    private List<@NotBlank @Size(max = 32) String> tags = new ArrayList<>();
    @NotNull @Size(max = 5)
    private List<@NotBlank @Size(max = 32) String> phones = new ArrayList<>();
    @NotNull @Size(max = 512)
    private String description = "";
}
