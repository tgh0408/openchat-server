package com.openchat.user.domain.dto;

import lombok.Data;

@Data
public class FileInfoDTO {
    private String fileName;
    private byte[] streamBytes;
}
