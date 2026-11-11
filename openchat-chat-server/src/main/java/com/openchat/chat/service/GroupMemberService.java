package com.openchat.chat.service;

import com.openchat.chat.dao.GroupMemberDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupMemberService {
    final GroupMemberDao groupMemberDao;

    public List<Long> getGroupMembersByRoomId(Long roomId) {
        return groupMemberDao.getMemberUidSet(roomId).stream().toList();
    }
}
