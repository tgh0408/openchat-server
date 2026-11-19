package com.openchat.rtc.autoconfigure;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 仅配置完整时注册回调验签组件和回调入口，避免可选媒体配置阻塞应用启动。 */
public class LiveKitConfiguredCondition extends SpringBootCondition {
    @Override
    public @NonNull ConditionOutcome getMatchOutcome(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        LiveKitProperties properties = Binder.get(context.getEnvironment())
                .bind("openchat.rtc.livekit", LiveKitProperties.class)
                .orElseGet(LiveKitProperties::new);
        return new ConditionOutcome(properties.isConfigured(), "LiveKit configuration availability");
    }
}
