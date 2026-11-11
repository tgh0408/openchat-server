package com.openchat.chat.domain.entity.msg;

import com.openchat.common.utils.discover.domain.UrlInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Description: 消息扩展属性
 * Author: 
 * Date: 2023-05-28
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageExtra implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    //url跳转链接
    private Map<String, UrlInfo> urlContentMap;
    //消息撤回详情
    private MsgRecall recall;
    //艾特的uid
    private List<Long> atUidList;
    //文件消息
    private FileMsgDTO fileMsg;
    //图片消息
    private ImgMsgDTO imgMsgDTO;
    //语音消息
    private SoundMsgDTO soundMsgDTO;
    //文件消息
    private VideoMsgDTO videoMsgDTO;
    // 视频和普通大文件共用的上传生命周期
    private TemporaryMsgDTO temporary;
    // 与该条占位绑定的 Multipart 和封面资源，不作为公开消息元数据。
    private MessageUpload upload;
    // RTC 通话终态摘要
    private RtcCallMsgDTO rtcCall;
    // 服务端系统提示完整正文快照；message.content 仅保存限长摘要。
    private String systemContent;
    // 每位入群成员独立的系统提示展示信息。
    private GroupInvitationMsgDTO groupInvitation;
    // 移出提示复用群广播，目标 UID 仅用于前端选择本人文案。
    private GroupRemovalMsgDTO groupRemoval;

    /**
     * 表情图片信息
     */
    private EmojisMsgDTO emojisMsgDTO;
}
