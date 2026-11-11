package com.openchat.common.utils;


import cn.hutool.core.lang.Assert;
import com.openchat.common.domain.dto.RequestInfo;

//使用ThreadLocal将解析出来的Token绑定到线程上
public class RequestHolder {
    private static final ThreadLocal<RequestInfo> threadLocal = new ThreadLocal<>();

    public static void set(RequestInfo requestInfo){
        threadLocal.set(requestInfo);
    }

    public static RequestInfo get(){
        return threadLocal.get();
    }

    public static void remove(){
        threadLocal.remove();
    }

    public static Long getUid(){
        Long uid = threadLocal.get().getUid();
        Assert.notNull(uid, "无法获取当前用户id");
        return uid;
    }

    public static String getToken(){
        String token = threadLocal.get().getToken();
        Assert.notBlank(token, "无法获取用户token");
        return token;
    }

    public static String getIp(){
        return threadLocal.get().getIp();
    }
}
