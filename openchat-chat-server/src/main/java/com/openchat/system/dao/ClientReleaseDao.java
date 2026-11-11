package com.openchat.system.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.common.exception.BusinessException;
import com.openchat.system.domain.entity.ClientRelease;
import com.openchat.system.mapper.ClientReleaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 发布记录只追加，不维护可被并发覆盖的当前版本配置。
 */
@Repository
@RequiredArgsConstructor
public class ClientReleaseDao {
    private final ClientReleaseMapper clientReleaseMapper;

    public ClientRelease getLatest() {
        return clientReleaseMapper.selectOne(new LambdaQueryWrapper<ClientRelease>()
                .orderByDesc(ClientRelease::getId)
                .last("LIMIT 1"));
    }

    public ClientRelease getById(Long releaseId) {
        return clientReleaseMapper.selectById(releaseId);
    }

    public List<ClientRelease> listReleases() {
        return clientReleaseMapper.selectList(new LambdaQueryWrapper<ClientRelease>()
                .orderByDesc(ClientRelease::getId));
    }

    /**
     * 同版本竞争交由唯一索引裁决，不能用先查后写代替数据库约束。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(ClientRelease release) {
        if (clientReleaseMapper.insert(release) != 1) {
            throw new BusinessException("客户端版本发布失败");
        }
    }
}
