package com.openchat.common.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 在不改变响应写出行为的前提下，复制响应内容供日志记录。
 */
public class ResponseCaptureWrapper extends HttpServletResponseWrapper {

    public static final String REQUEST_ATTRIBUTE = ResponseCaptureWrapper.class.getName() + ".INSTANCE";

    private final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public ResponseCaptureWrapper(HttpServletResponse response) {
        super(response);
    }

    public byte[] getCapturedBody() {
        if (writer != null) {
            writer.flush();
        }
        return capturedBody.toByteArray();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (outputStream == null) {
            ServletOutputStream delegate = super.getOutputStream();
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int value) throws IOException {
                    delegate.write(value);
                    capturedBody.write(value);
                }

                @Override
                public void write(byte @NonNull [] bytes, int offset, int length) throws IOException {
                    delegate.write(bytes, offset, length);
                    capturedBody.write(bytes, offset, length);
                }

                @Override
                public void flush() throws IOException {
                    delegate.flush();
                }

                @Override
                public void close() throws IOException {
                    delegate.close();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    delegate.setWriteListener(listener);
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), charset));
        }
        return writer;
    }
}
