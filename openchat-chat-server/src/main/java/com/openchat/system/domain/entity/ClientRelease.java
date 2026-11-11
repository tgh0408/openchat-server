package com.openchat.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不可覆盖的客户端发布记录，每条记录对应一个已上传的安装包。
 */
@Data
@TableName("client_release")
public class ClientRelease {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String versionName;
    private String clientName;
    private String bucketName;
    private String objectKey;
    private Long fileSize;
    private String sha256;
    private String updateInfo;
    private LocalDateTime publishedAt;
}
