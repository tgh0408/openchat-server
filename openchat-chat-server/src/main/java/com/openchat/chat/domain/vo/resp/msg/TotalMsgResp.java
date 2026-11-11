package com.openchat.chat.domain.vo.resp.msg;

import com.fasterxml.jackson.annotation.JsonValue;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.common.utils.discover.domain.UrlInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;
import java.util.Map;

/**
 * Description: 文本消息返回体
 * Author: 
 * Date: 2023-06-04
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TotalMsgResp {
    @Schema(description = "消息内容")
    private String content;
    @Schema(description = "消息拓展信息")
    private Object extra;
    @Schema(description = "消息链接映射")
    private Map<String, UrlInfo> urlContentMap;
    @Schema(description = "艾特的uid")
    private List<Long> atUidList;
    @Schema(description = "父消息，如果没有父消息，返回的是null")
    private ReplyMsg reply;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReplyMsg {
        @Schema(description = "消息id")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long replyId;
        @Schema(description = "用户uid")
        private Long gapCount;
    }
}
