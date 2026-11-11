package com.openchat.common.aspect;

import com.openchat.common.annotation.RedissonLock;
import com.openchat.common.service.LockService;
import com.openchat.common.utils.SpElUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
@Aspect
@Order(0) //确保注解比事务的注解先执行,分布式锁在事务外
@RequiredArgsConstructor
public class RedissonLockAspect {
    final LockService lockService;

    @Around("@annotation(com.openchat.common.annotation.RedissonLock)")
    public Object around(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RedissonLock redissonLock = method.getAnnotation(RedissonLock.class);
        String prefixKey = StringUtils.isBlank(redissonLock.prefixKey()) ? SpElUtils.getMethodKey(method) : redissonLock.prefixKey();
        String key = SpElUtils.parseSpEl(method, joinPoint.getArgs(), redissonLock.key());
        return lockService.lock(prefixKey+":"+key,redissonLock.waitTime(),redissonLock.unit(),joinPoint::proceed);
    }

}
