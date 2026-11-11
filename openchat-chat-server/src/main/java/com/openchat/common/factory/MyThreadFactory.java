package com.openchat.common.factory;

import com.openchat.common.thread.MyUncaughtExceptionHandler;
import lombok.NonNull;

import java.util.concurrent.ThreadFactory;

public class MyThreadFactory implements ThreadFactory {
    private static final MyUncaughtExceptionHandler myUncaughtExceptionHandler = new MyUncaughtExceptionHandler();

    public MyThreadFactory(ThreadFactory factory){
        this.oldFactory = factory;
    }
    private final ThreadFactory oldFactory;

    @Override
    public Thread newThread(@NonNull Runnable r) {
        Thread thread = oldFactory.newThread(r);
        thread.setUncaughtExceptionHandler(myUncaughtExceptionHandler);
        return thread;
    }
}
