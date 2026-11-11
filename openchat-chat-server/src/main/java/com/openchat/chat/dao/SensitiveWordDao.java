package com.openchat.chat.dao;

import com.openchat.chat.domain.entity.SensitiveWord;
import com.openchat.chat.mapper.SensitiveWordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 敏感词库 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2024-08-27
 */
@Repository
@RequiredArgsConstructor
public class SensitiveWordDao {
    private final SensitiveWordMapper sensitiveWordMapper;

    /** 读取敏感词库，供词库工厂加载。 */
    public List<SensitiveWord> list() {
        return sensitiveWordMapper.selectList(new LambdaQueryWrapper<SensitiveWord>());
    }
}
