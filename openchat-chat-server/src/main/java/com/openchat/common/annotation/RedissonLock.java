package com.openchat.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 注解分布式锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RedissonLock {

    String prefixKey() default "";

    /**
     * 支持springEl表达式的key
     */
    String key() default "";

    /**
     * 等待排序时间，默认快速失败
     */
    int waitTime() default -1;

    /**
     * 等待时间单位,默认毫秒
     */
    TimeUnit unit() default  TimeUnit.MILLISECONDS;
}
