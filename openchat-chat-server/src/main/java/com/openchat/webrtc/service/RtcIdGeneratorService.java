package com.openchat.webrtc.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.openchat.rtc.port.RtcIdGenerator;
import org.springframework.stereotype.Component;

/**
 * 复用 MyBatis-Plus 的分布式 ID 生成器。
 */
@Component
public class RtcIdGeneratorService implements RtcIdGenerator {
    @Override
    public String nextId() {
        return Long.toString(IdWorker.getId());
    }
}
