package com.openchat.webrtc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;

/** 媒体回调只复用标准 INSERT，无自定义 XML SQL。 */
public interface RtcWebhookEventMapper extends BaseMapper<RtcWebhookEvent> {
}
