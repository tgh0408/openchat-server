package com.openchat.chat.service;

import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 注册时初始化用户独立的文件助手，所有接口和推送统一使用真实房间 ID。
 */
@Service
@RequiredArgsConstructor
public class FileAssistantService {
    public static final String NAME = "文件助手";

    private final RoomDao roomDao;
    private final ContactDao contactDao;

    @Transactional(rollbackFor = Exception.class)
    public Room getOrCreate(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BusinessException("文件助手所属用户 ID 无效");
        }
        // 房间创建与会话初始化处于同一事务，唯一索引负责跨实例并发去重。
        Room room = roomDao.getOrCreateFileAssistant(uid);
        if (room == null || !Objects.equals(room.getOwnerUid(), uid)
                || room.getType() != RoomTypeEnum.FILE_ASSISTANT) {
            throw new BusinessException("文件助手房间初始化失败");
        }
        contactDao.createMissingContacts(room.getId(), List.of(uid));
        return room;
    }
}
