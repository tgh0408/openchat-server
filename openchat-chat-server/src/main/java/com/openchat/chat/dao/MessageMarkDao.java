package com.openchat.chat.dao;

import com.openchat.chat.domain.entity.MessageMark;
import com.openchat.chat.mapper.MessageMarkMapper;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 消息标记表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-12-24
 */
@Repository
@RequiredArgsConstructor
public class MessageMarkDao {
    private final MessageMarkMapper messageMarkMapper;

    public List<MessageMark> getValidMarkByMsgIdBatch(List<Long> msgIds) {
        return messageMarkMapper.selectList(new LambdaQueryWrapper<MessageMark>()
                .in(MessageMark::getMsgId, msgIds)
                .eq(MessageMark::getStatus, YesOrNoEnum.NO));
    }
}
