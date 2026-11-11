package com.openchat.common.config;

import com.openchat.common.factory.MyWordFactory;
import com.openchat.common.utils.sensitiveWord.ACFilter;
import com.openchat.common.utils.sensitiveWord.DFAFilter;
import com.openchat.common.utils.sensitiveWord.SensitiveWordBs;
import com.openchat.common.utils.sensitiveWord.SensitiveWordFilter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SensitiveWordConfig.SensitiveWordProperties.class)
public class SensitiveWordConfig {

    @Getter
    @Setter
    @ConfigurationProperties(prefix = "sensitive-word")
    public static class SensitiveWordProperties {

        /**
         * 是否启用敏感词过滤。
         */
        private boolean enabled = true;

        /**
         * 敏感词过滤算法。
         */
        private FilterType filter = FilterType.AC;
    }

    /**
     * 敏感词过滤算法类型。
     */
    public enum FilterType {
        AC,
        DFA
    }

    private final MyWordFactory myWordFactory;

    /**
     * 初始化引导类
     *
     * @return 初始化引导类
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "sensitive-word",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SensitiveWordBs sensitiveWordBs(SensitiveWordProperties sensitiveWordProperties) {
        log.info("敏感词过滤加载");
        return SensitiveWordBs.newInstance()        // 方法调用逻辑
                .filterStrategy(createFilter(sensitiveWordProperties.getFilter())) //设置过滤逻辑
                .sensitiveWord(myWordFactory) //提供敏感词列表
                .init();
    }

    /**
     * 根据配置创建敏感词过滤器。
     *
     * @param filterType 过滤算法类型
     * @return 敏感词过滤器
     */
    private SensitiveWordFilter createFilter(FilterType filterType) {
        if (filterType == null) {
            return ACFilter.getInstance();
        }
        return switch (filterType) {
            case AC -> ACFilter.getInstance();
            case DFA -> DFAFilter.getInstance();
        };
    }

}
