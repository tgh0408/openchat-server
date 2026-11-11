package com.openchat.chat.service;

import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.domain.entity.Contact;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.ContactStateEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.dao.ContactDao;
import com.openchat.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 已读确认只同步本人会话状态，不授予消息读取权限；跨节点单调性由数据库条件更新保证。 */
@Service
@RequiredArgsConstructor
public class ContactReadService {
    private final RoomAccessService roomAccessService;
    private final MessageDao messages;
    private final ContactDao contacts;

    @Transactional(rollbackFor = Exception.class)
    public Long acknowledge(Long uid, Long roomId, Long msgId) {
        // 本人的已有会话是状态同步边界；离群或好友关系失效不阻断已提交的阅读待办。
        Contact contact = contacts.getContactStatus(roomId, uid);
        if (contact == null || contact.getContactState() != ContactStateEnum.NORMAL) {
            throw new BusinessException("会话不存在");
        }
        Room room = roomAccessService.requireRoom(roomId);
        long read = contact.getLastReadMsgId() == null ? 0L : contact.getLastReadMsgId();
        long deleted = contact.getLastDeleteId() == null ? 0L : contact.getLastDeleteId();
        if (msgId <= deleted) return Math.max(read, deleted);
        if (msgId <= read) return read;
        Message message = messages.getById(room.getType(), msgId);
        if (message == null || !Objects.equals(message.getRoomId(), roomId)
                || message.getStatus() != com.openchat.common.domain.enums.YesOrNoEnum.NO) {
            throw new BusinessException("已读消息不存在或不可见");
        }
        if (contacts.markReadSync(uid, roomId, msgId) != 1) {
            throw new BusinessException("已读确认未完成，请重试");
        }
        return msgId;
    }
}
