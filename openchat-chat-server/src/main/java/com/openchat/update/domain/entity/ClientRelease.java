package com.openchat.update.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.Data;

import java.time.LocalDateTime;

/** Dashboard 写入的共享发布记录，聊天节点只读取当前已提交快照。 */
@Data
@TableName("client_release")
public class ClientRelease {
    @TableId(type = IdType.AUTO)
    private Long id;
    private TerminalEnum terminal;
    private String versionName;
    private String clientName;
    private String bucketName;
    private String objectKey;
    private Long fileSize;
    private String sha256;
    private String updateInfo;
    private LocalDateTime publishedAt;
}
