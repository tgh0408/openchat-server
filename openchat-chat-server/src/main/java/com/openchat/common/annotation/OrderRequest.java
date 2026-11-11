package com.openchat.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 顺序消息接口(相同键名的情况下按照指定的接口顺序执行)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OrderRequest {

    String prefixKey() default "";

    /**
     * 特殊标识符
     */
    String specialKey() default "";

    /**
     * 支持springEl表达式的key
     */
    String [] key() default {};

    /**
     * 等待排序时间，默认快速失败
     */
    int waitTime() default 200;

    /**
     * 等待时间单位,默认毫秒
     */
    TimeUnit unit() default  TimeUnit.MILLISECONDS;

    /**
     * 当前方法序号
     */
    int order() default 0;

    /**
     * 总重试次数
     */
    int RetryCount() default 10;

}
