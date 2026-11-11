package com.openchat.chat.domain.entity.msg;

import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 服务端上传控制记录，只存于消息扩展字段，不通过消息正文返回。 */
@Data
public class MessageUpload implements Serializable {
    private String sessionKey;
    private String uploadId;
    private String objectPath;
    private List<String> posterPaths = new ArrayList<>();
}
