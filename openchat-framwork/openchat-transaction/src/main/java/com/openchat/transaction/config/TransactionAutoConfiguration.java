package com.openchat.transaction.config;

import com.openchat.transaction.annotation.SecureInvokeConfigurer;
import com.openchat.transaction.aspect.SecureInvokeAspect;
import com.openchat.transaction.dao.SecureInvokeRecordDao;
import com.openchat.transaction.service.MQProducer;
import com.openchat.transaction.service.SecureInvokeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.function.SingletonSupplier;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Description:
 * Author: 
 * Date: 2023-08-06
 *
 */
@Configuration
@Import({ SecureInvokeAspect.class,SecureInvokeRecordDao.class, LiteTopicPushConfiguration.class})
public class TransactionAutoConfiguration {

    @Nullable
    protected Executor executor;

    /**
     * Collect any {@link AsyncConfigurer} beans through autowiring.
     */
    @Autowired
    void setConfigurers(ObjectProvider<SecureInvokeConfigurer> configurers) {
        Supplier<SecureInvokeConfigurer> configurer = SingletonSupplier.of(() -> {
            List<SecureInvokeConfigurer> candidates = configurers.stream().toList();
            if (CollectionUtils.isEmpty(candidates)) {
                return null;
            }
            if (candidates.size() > 1) {
                throw new IllegalStateException("Only one SecureInvokeConfigurer may exist");
            }
            return candidates.get(0);
        });
         executor = Optional
                .ofNullable(configurer.get())
                .map(SecureInvokeConfigurer::getSecureInvokeExecutor)
                .orElseThrow(() -> new IllegalStateException(
                        "SecureInvokeConfigurer must provide a managed executor"));
    }

    @Bean
    public SecureInvokeService getSecureInvokeService(SecureInvokeRecordDao dao,
                                                     PlatformTransactionManager transactions) {
        return new SecureInvokeService(dao, executor, transactions);
    }

    @Bean
    public MQProducer getMQProducer() {
        return new MQProducer();
    }
}
