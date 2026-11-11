package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.EmojisMsgDTO;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.msg.EmojiMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.service.MinioBucketService;
import com.openchat.user.dao.EmojiDao;
import com.openchat.user.dao.UserEmojiDao;
import com.openchat.user.domain.entity.Emoji;
import com.openchat.user.domain.entity.UserEmoji;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmojiMsgHandler extends AbstractMsgHandler<EmojiMsgReq>{
    final EmojiDao emojiDao;
    final UserEmojiDao userEmojiDao;
    final OssProperties ossProperties;
    final MinioBucketService minioBucketService;
    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.EMOJI;
    }

    @Override
    public void checkMsg(EmojiMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
        UserEmoji owned = userEmojiDao.getUserEmoji(uid, body.getEmojiId());
        if (owned == null || owned.getDeleteStatus() == com.openchat.common.domain.enums.YesOrNoEnum.YES) {
            throw new BusinessException("只能发送自己收藏的表情包");
        }
        // 提交 MQ 前和本地事务中均执行，失效收藏不能仅凭图片 ID 发送。
        requireEmoji(body.getEmojiId());
    }

    @Override
    public void fillMsgContent(Message message, EmojiMsgReq body, RoomTypeEnum roomTypeEnum) {
        Emoji emoji = requireEmoji(body.getEmojiId());
        EmojisMsgDTO emojiMsgDTO = new EmojisMsgDTO();
        emojiMsgDTO.setEmojiId(emoji.getId());
        emojiMsgDTO.setDescription(emoji.getDescription());
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        extra.setEmojisMsgDTO(emojiMsgDTO);
        message.setExtra(extra);
        message.setContent(emoji.getUrl());
    }

    @Override
    public Object showMsg(Message msg) {
        TotalMsgResp resp = new TotalMsgResp();
        resp.setContent(OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(),
                minioBucketService.currentBucket(),
                msg.getContent()));
        resp.setExtra(Optional.ofNullable(msg.getExtra())
                .map(MessageExtra::getEmojisMsgDTO)
                .orElse(null));
        resp.setUrlContentMap(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getUrlContentMap).orElse(null));
        resp.setAtUidList(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getAtUidList).orElse(null));
        return resp;
    }

    private Emoji requireEmoji(Long emojiId) {
        Emoji emoji = emojiDao.get(emojiId);
        if (emoji == null) {
            throw new BusinessException("此表情包不存在请检查");
        }
        OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(),
                minioBucketService.currentBucket(),
                emoji.getUrl());
        return emoji;
    }

    @Override
    public Object showReplyMsg(Message msg) {
        return OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(),
                minioBucketService.currentBucket(),
                msg.getContent());
    }

    @Override
    public String showContactMsg(Message msg) {
        return Optional.ofNullable(msg.getExtra())
                .map(MessageExtra::getEmojisMsgDTO)
                .map(EmojisMsgDTO::getDescription)
                .filter(description -> !description.isBlank())
                .orElse("[表情包]");
    }
}
