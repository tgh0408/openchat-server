package com.openchat.user.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplyChat {
    private Long uid;
    private String msg;
}
