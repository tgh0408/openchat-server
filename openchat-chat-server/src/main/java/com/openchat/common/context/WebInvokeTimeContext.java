package com.openchat.common.context;

import jakarta.servlet.ServletException;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Web 请求耗时统计上下文。
 */
public final class WebInvokeTimeContext {

    private static final ScopedValue<StopWatch> STOP_WATCH = ScopedValue.newInstance();

    private WebInvokeTimeContext() {
    }

    public static void run(Invocation invocation) throws IOException, ServletException {
        try {
            ScopedValue.where(STOP_WATCH, new StopWatch()).call(() -> {
                invocation.run();
                return null;
            });
        } catch (IOException | ServletException | RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServletException(exception);
        }
    }

    /**
     * 未经过过滤器的调用不参与计时，不能让辅助日志覆盖原始请求异常。
     */
    public static @Nullable StopWatch getStopWatch() {
        return STOP_WATCH.isBound() ? STOP_WATCH.get() : null;
    }

    @FunctionalInterface
    public interface Invocation {

        void run() throws IOException, ServletException;
    }
}
