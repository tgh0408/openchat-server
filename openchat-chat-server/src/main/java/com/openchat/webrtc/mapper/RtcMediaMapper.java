package com.openchat.webrtc.mapper;

import com.openchat.webrtc.domain.entity.RtcMediaSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 所有访问先锁 rtc_call；额外保留版本及传输模式条件拒绝迟到写入。 */
public interface RtcMediaMapper extends BaseMapper<RtcMediaSession> {
}
