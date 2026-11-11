package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.common.exception.BusinessException;

import java.util.HashMap;
import java.util.Map;

/**
 * Description:
 * Author: 
 * Date: 2023-06-04
 */
public class MsgHandlerFactory {
    private static final Map<MessageTypeEnum, AbstractMsgHandler<?>> STRATEGY_MAP = new HashMap<>();

    public static void register(MessageTypeEnum code, AbstractMsgHandler<?> strategy) {
        STRATEGY_MAP.put(code, strategy);
    }

    public static AbstractMsgHandler<?> getStrategyNoNull(MessageTypeEnum code) {
        AbstractMsgHandler<?> strategy = STRATEGY_MAP.get(code);
        if (strategy == null) {
            throw new BusinessException("未找到数据类型适配器");
        }
        return strategy;
    }
}
