package com.openchat.chat.domain.entity.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Description: 消息撤回
 * Author: 
 * Date: 2023-06-04
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MsgRecall implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    //撤回消息的uid
    private Long recallUid;
    //撤回的时间点
    private Date recallTime;
    // 服务端对象墓碑，不向客户端返回，用于清理重试和拒绝迟到重新引用。
    private List<String> objectPaths;
    private MessageUpload upload;
}
