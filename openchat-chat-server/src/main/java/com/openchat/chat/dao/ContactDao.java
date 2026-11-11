package com.openchat.chat.dao;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.openchat.chat.domain.entity.Contact;
import com.openchat.chat.domain.enums.ContactStateEnum;
import com.openchat.chat.mapper.ContactMapper;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.domain.vo.req.TopOrUnTopReq;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Contact persistence and state transitions.
 */
@Repository
@RequiredArgsConstructor
public class ContactDao {
    private final ContactMapper contactMapper;

    /**
     * Saves a new contact.
     */
    public boolean save(Contact contact) {
        return contactMapper.insert(contact) > 0;
    }

    /**
     * Advances the history boundary and changes the display state atomically by version.
     */
    public void clearHistory(Long uid, Long roomId, Long lastDeleteId, boolean group) {
        Contact current = getContactStatus(roomId, uid);
        if (current == null) {
            throw new BusinessException("清空聊天记录未完成，请重试");
        }
        if (current.getLastDeleteId() != null && current.getLastDeleteId() > lastDeleteId) {
            return;
        }
        ContactStateEnum nextState = contactMapper.hasMessageAfter(uid, roomId, lastDeleteId, group)
                ? ContactStateEnum.NORMAL : ContactStateEnum.DELETED;
        current.setContactState(nextState);
        current.setLastDeleteId(lastDeleteId);
        if (contactMapper.updateById(current) == 1) {
            return;
        }
        Contact latest = getContactStatus(roomId, uid);
        if (latest == null || latest.getLastDeleteId() == null
                || latest.getLastDeleteId() < lastDeleteId
                || (latest.getLastDeleteId().equals(lastDeleteId)
                && latest.getContactState() != nextState)) {
            throw new BusinessException("清空聊天记录未完成，请重试");
        }
    }

    /**
     * Reads only a visible contact.
     */
    public Contact get(Long uid, Long roomId) {
        return contactMapper.selectOne(new LambdaQueryWrapper<Contact>()
                .eq(Contact::getUid, uid)
                .eq(Contact::getRoomId, roomId)
                .eq(Contact::getContactState, ContactStateEnum.NORMAL));
    }

    @Async
    public void markRead(Long uid, Long roomId, Long msgId) {
        markReadSync(uid, roomId, msgId);
    }

    /**
     * Advances the read cursor only for a normal contact.
     */
    public int markReadSync(Long uid, Long roomId, Long msgId) {
        Contact current = getContactStatus(roomId, uid);
        if (current == null || current.getContactState() != ContactStateEnum.NORMAL) {
            return 0;
        }
        Long lastRead = current.getLastReadMsgId();
        if (lastRead != null && lastRead >= msgId) {
            return 0;
        }
        current.setLastReadMsgId(msgId);
        return contactMapper.updateById(current);
    }

    /**
     * Reads a contact including deleted and hidden states.
     */
    public Contact getContactStatus(Long roomId, Long uid) {
        return contactMapper.selectOne(new LambdaQueryWrapper<Contact>()
                .eq(Contact::getUid, uid)
                .eq(Contact::getRoomId, roomId));
    }

    /**
     * Restores a contact explicitly without changing read or history boundaries.
     */
    public void saveOrRestore(Contact contact) {
        contactMapper.saveOrRestore(contact);
        Contact current = getContactStatus(contact.getRoomId(), contact.getUid());
        if (current != null && current.getContactState() != ContactStateEnum.NORMAL) {
            current.setContactState(ContactStateEnum.NORMAL);
            if (contactMapper.updateById(current) != 1) {
                throw new BusinessException("恢复会话失败，请重试");
            }
        }
    }

    public void createMissingContacts(Long roomId, List<Long> uids) {
        if (CollectionUtil.isNotEmpty(uids)) {
            contactMapper.createMissingContacts(roomId, uids);
        }
    }

    /** 入群时新会话直接写入提示 ID 减一；已有会话合并恢复显示和边界推进。 */
    public void advanceInvitationBoundary(Long uid, Long roomId, Long messageId) {
        if (uid == null || uid <= 0 || roomId == null || roomId <= 0
                || messageId == null || messageId <= 0) {
            throw new BusinessException("无效的入群消息边界");
        }
        long boundary = messageId - 1;
        Contact current = getContactStatus(roomId, uid);
        if (current == null) {
            Contact contact = new Contact();
            contact.setUid(uid);
            contact.setRoomId(roomId);
            contact.setLastDeleteId(boundary);
            contact.setLastReadMsgId(0L);
            contact.setContactState(ContactStateEnum.NORMAL);
            contact.setVersion(0L);
            // 并发创建由 (uid, room_id) 唯一索引兜底，冲突向外传播并回滚整个入群事务。
            if (contactMapper.insert(contact) != 1) {
                throw new BusinessException("创建入群会话失败，请重试");
            }
            return;
        }
        current.setLastDeleteId(Math.max(
                Optional.ofNullable(current.getLastDeleteId()).orElse(0L), boundary));
        current.setContactState(ContactStateEnum.NORMAL);
        if (contactMapper.updateById(current) != 1) {
            throw new BusinessException("恢复入群会话失败，请重试");
        }
    }

    /**
     * Reads all contact states for list refresh.
     */
    public List<Contact> getAllContactList(Long uid) {
        return contactMapper.selectList(new LambdaQueryWrapper<Contact>()
                .eq(Contact::getUid, uid));
    }

    /**
     * Restores a deleted or hidden contact when a newer room message exists.
     */
    public boolean restoreIfNewMessage(Contact contact, Long latestMsgId) {
        if (contact == null || contact.getId() == null || contact.getVersion() == null
                || (contact.getContactState() != ContactStateEnum.DELETED
                && contact.getContactState() != ContactStateEnum.HIDDEN)
                || latestMsgId == null || latestMsgId <= 0) {
            return false;
        }
        long boundary = Math.max(
                Optional.ofNullable(contact.getLastDeleteId()).orElse(0L),
                Optional.ofNullable(contact.getLastHiddenMsgId()).orElse(0L));
        if (latestMsgId <= boundary) {
            return false;
        }
        long nextVersion = contact.getVersion() + 1;
        LambdaUpdateWrapper<Contact> update = new LambdaUpdateWrapper<>();
        update.eq(Contact::getId, contact.getId())
                .eq(Contact::getUid, contact.getUid())
                .eq(Contact::getRoomId, contact.getRoomId())
                .eq(Contact::getVersion, contact.getVersion())
                .eq(Contact::getContactState, contact.getContactState())
                .set(Contact::getContactState, ContactStateEnum.NORMAL)
                .set(Contact::getVersion, nextVersion)
                .set(Contact::getUpdateTime, new Date());
        if (contact.getContactState() == ContactStateEnum.DELETED) {
            update.and(wrapper -> wrapper.isNull(Contact::getLastDeleteId)
                    .or()
                    .lt(Contact::getLastDeleteId, latestMsgId));
        } else {
            update.and(wrapper -> wrapper.isNull(Contact::getLastHiddenMsgId)
                    .or()
                    .lt(Contact::getLastHiddenMsgId, latestMsgId));
        }
        int updated = contactMapper.update(null, update);
        if (updated != 1) {
            return false;
        }
        contact.setContactState(ContactStateEnum.NORMAL);
        contact.setVersion(nextVersion);
        return true;
    }

    /**
     * Reads only normal contacts by room.
     */
    public List<Contact> getByRoomIds(List<Long> roomIds, Long uid) {
        return contactMapper.selectList(new LambdaQueryWrapper<Contact>()
                .in(Contact::getRoomId, roomIds)
                .eq(Contact::getUid, uid)
                .eq(Contact::getContactState, ContactStateEnum.NORMAL));
    }

    public boolean changeTopStatus(Long uid, TopOrUnTopReq request) {
        Contact current = getContactStatus(request.getRoomId(), uid);
        YesOrNoEnum status = request.getStatus();
        if (current == null || current.getContactState() != ContactStateEnum.NORMAL
                || current.getIsTop() == status) {
            return false;
        }
        current.setIsTop(status);
        return contactMapper.updateById(current) > 0;
    }

    /** 用局部实体只更新免打扰，版本校验和递增交由乐观锁插件处理。 */
    public void changeNotDisturb(Long uid, Long roomId, YesOrNoEnum status) {
        Contact current = getContactStatus(roomId, uid);
        if (current == null) throw new BusinessException("会话不存在");
        if (Optional.ofNullable(current.getNotDisturb()).orElse(YesOrNoEnum.NO) == status) return;
        if (current.getVersion() == null) throw new BusinessException("会话版本无效，请重试");
        Contact update = new Contact();
        update.setId(current.getId());
        update.setVersion(current.getVersion());
        update.setNotDisturb(status);
        update.setUpdateTime(new Date());
        if (contactMapper.updateById(update) == 1) return;
        Contact latest = getContactStatus(roomId, uid);
        if (latest == null || latest.getNotDisturb() != status) {
            throw new BusinessException("会话状态已变化，请重新获取后重试");
        }
    }

    /**
     * Reads a normal contact for access checks.
     */
    public Contact getByRoomIdAndUid(Long roomId, Long uid) {
        return contactMapper.selectOne(new LambdaQueryWrapper<Contact>()
                .eq(Contact::getRoomId, roomId)
                .eq(Contact::getUid, uid)
                .eq(Contact::getContactState, ContactStateEnum.NORMAL));
    }

    /**
     * Explicitly restores any non-normal contact.
     */
    public void changeContactStatus(@NotNull Long roomId, Long uid) {
        Contact current = getContactStatus(roomId, uid);
        if (current == null || current.getContactState() == ContactStateEnum.NORMAL) {
            return;
        }
        current.setContactState(ContactStateEnum.NORMAL);
        if (contactMapper.updateById(current) != 1) {
            throw new BusinessException("恢复会话失败，请重试");
        }
    }

    /**
     * Hides a normal contact and records the current room message boundary.
     */
    public void hideContact(Long roomId, Long uid, Long latestMsgId) {
        Contact current = getContactStatus(roomId, uid);
        if (current == null) {
            throw new BusinessException("会话不存在");
        }
        if (current.getContactState() == ContactStateEnum.HIDDEN) {
            return;
        }
        if (current.getContactState() != ContactStateEnum.NORMAL || current.getVersion() == null) {
            throw new BusinessException("会话状态已变化，请重试");
        }
        long nextVersion = current.getVersion() + 1;
        LambdaUpdateWrapper<Contact> update = new LambdaUpdateWrapper<>();
        update.eq(Contact::getId, current.getId())
                .eq(Contact::getUid, uid)
                .eq(Contact::getRoomId, roomId)
                .eq(Contact::getVersion, current.getVersion())
                .eq(Contact::getContactState, ContactStateEnum.NORMAL)
                .set(Contact::getContactState, ContactStateEnum.HIDDEN)
                .set(Contact::getLastHiddenMsgId, latestMsgId)
                .set(Contact::getVersion, nextVersion)
                .set(Contact::getUpdateTime, new Date());
        if (contactMapper.update(null, update) != 1) {
            Contact latest = getContactStatus(roomId, uid);
            if (latest == null || latest.getContactState() != ContactStateEnum.HIDDEN) {
                throw new BusinessException("隐藏会话失败，请重试");
            }
        }
    }
}
