package com.openchat.user.dao;

import com.openchat.user.domain.entity.UserRole;
import com.openchat.user.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openchat.user.domain.event.UserRoleChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 用户角色关系表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-11-28
 */
@Repository
@RequiredArgsConstructor
public class UserRoleDao {
    private final UserRoleMapper userRoleMapper;
    private final ApplicationEventPublisher events;

    @Transactional(rollbackFor = Exception.class)
    public boolean save(UserRole role) {
        boolean saved = userRoleMapper.insert(role) > 0;
        if (saved) events.publishEvent(new UserRoleChanged(role.getUid()));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(UserRole role) {
        UserRole stored = userRoleMapper.selectById(role.getId());
        if (stored == null) return false;
        if (role.getUid() != null && !role.getUid().equals(stored.getUid()))
            throw new IllegalArgumentException("角色更新不能更换所属用户");
        if (role.getVersion() == null) role.setVersion(stored.getVersion());
        boolean updated = userRoleMapper.updateById(role) > 0;
        if (updated) events.publishEvent(new UserRoleChanged(stored.getUid()));
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        UserRole stored = userRoleMapper.selectById(id);
        if (stored == null) return false;
        boolean removed = userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getId, id)
                .eq(UserRole::getVersion, stored.getVersion())) > 0;
        if (removed) events.publishEvent(new UserRoleChanged(stored.getUid()));
        return removed;
    }

    /** 最终撤回授权不使用进程本地角色缓存，防止跨节点角色撤销后的迟到操作。 */
    public List<UserRole> listByUid(Long uid) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUid, uid));
    }

    /** 管理员登录实时核对角色，不使用角色缓存。 */
    public boolean hasRole(Long uid, Long roleId) {
        return userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUid, uid).eq(UserRole::getRoleId, roleId)) > 0;
    }
}
