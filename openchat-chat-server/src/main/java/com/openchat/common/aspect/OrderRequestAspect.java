package com.openchat.common.aspect;

import com.openchat.common.annotation.OrderRequest;
import com.openchat.common.constant.RedisKey;
import com.openchat.common.utils.RedisUtils;
import com.openchat.common.utils.SpElUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Aspect
@RequiredArgsConstructor
public class OrderRequestAspect {

    @Around("@annotation(com.openchat.common.annotation.OrderRequest)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        OrderRequest orderRequest = method.getAnnotation(OrderRequest.class);

        String specialKey = orderRequest.specialKey();
        String[] keys = orderRequest.key();
        String key = orderRequest.prefixKey();

        if (!Objects.equals(specialKey, "")) {
            String spEl = SpElUtils.parseSpEl(method, joinPoint.getArgs(), specialKey);
            key = String.join("_", key, spEl);
        }

        List<Long> keyList = new ArrayList<>();
        for (String k : keys) {
            String spEl = SpElUtils.parseSpEl(method, joinPoint.getArgs(), k);
            keyList.add(Long.parseLong(spEl));
        }
        List<Long> longList = keyList.stream().sorted().toList();
        key = key + "_" + longList.get(0) + "_" + longList.get(1);

        int retryCount = orderRequest.RetryCount();
        int waitTime = orderRequest.waitTime();
        TimeUnit timeUnit = orderRequest.unit();

        int order = orderRequest.order();
        while (retryCount > 0) {
            List<?> sList = Optional.ofNullable(
                    RedisUtils.lGet(
                            RedisKey.getKey(RedisKey.SIGNAL_STRING, key), 0, -1)
                    ).orElse(Collections.emptyList());
            List<Integer> signalList = sList.stream().map(s -> Integer.parseInt((String) s)).toList();
            if (order == 0 || signalList.contains(order - 1)) {
                try {
                    return joinPoint.proceed();
                } finally {
                    RedisUtils.lSet(RedisKey.getKey(RedisKey.SIGNAL_STRING, key), order,5);
                }
            }
            timeUnit.sleep(waitTime);
            retryCount--;
        }
        return joinPoint.proceed();
    }
}
