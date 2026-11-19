package com.openchat.update.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.update.domain.entity.ClientRelease;
import com.openchat.update.mapper.ClientReleaseMapper;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 发布查询没有缓存，各节点从同一数据库读取，避免发布后保留节点内旧版本。 */
@Repository
@RequiredArgsConstructor
public class ClientReleaseDao {
    private final ClientReleaseMapper mapper;

    public ClientRelease getLatest(TerminalEnum terminal) {
        return mapper.selectOne(new LambdaQueryWrapper<ClientRelease>()
                .eq(ClientRelease::getTerminal, terminal)
                .orderByDesc(ClientRelease::getId)
                .last("LIMIT 1"));
    }

    public ClientRelease getById(Long releaseId) {
        return mapper.selectById(releaseId);
    }
}
