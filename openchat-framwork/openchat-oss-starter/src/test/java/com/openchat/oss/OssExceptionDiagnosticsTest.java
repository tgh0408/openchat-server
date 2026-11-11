package com.openchat.oss;

import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** 核对日志保留定位信息，但不会输出 SDK trace 或嵌入消息的凭据。 */
public class OssExceptionDiagnosticsTest {
    @Test
    public void preservesTypesMessagesFramesCausesAndSuppressedWithoutChangingOriginal() {
        SocketTimeoutException root = new SocketTimeoutException("Read timed out");
        IllegalStateException original = new IllegalStateException("list parts failed", root);
        IllegalArgumentException cleanup = new IllegalArgumentException("abort failed");
        original.addSuppressed(cleanup);

        Throwable safe = OssExceptionDiagnostics.sanitized(original);
        String rendered = render(safe);

        assertTrue(rendered.contains(IllegalStateException.class.getName()));
        assertTrue(rendered.contains("list parts failed"));
        assertTrue(rendered.contains(SocketTimeoutException.class.getName()));
        assertTrue(rendered.contains("Read timed out"));
        assertTrue(rendered.contains("Suppressed:"));
        assertTrue(rendered.contains("abort failed"));
        assertArrayEquals(original.getStackTrace(), safe.getStackTrace());
        assertSame(root, original.getCause());
        assertSame(cleanup, original.getSuppressed()[0]);
        assertNotSame(root, safe.getCause());
    }

    @Test
    public void keepsSdkErrorCodeStatusAndRequestIdWithoutSdkHeadersOrTrace() {
        Request request = new Request.Builder()
                .url("https://storage.invalid/file?X-Amz-Signature=private-signature")
                .header("Authorization", "Bearer private-request-token").build();
        Response response = new Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                .header("Set-Cookie", "private-response-cookie").build();
        ErrorResponse error = new ErrorResponse("AccessDenied", "permission denied",
                "test-bucket", "file.bin", "/file.bin", "minio-request-123", "host-id");
        ErrorResponseException original = new ErrorResponseException(error, response,
                "http trace with private-trace-secret");

        String rendered = render(OssExceptionDiagnostics.sanitized(original));

        assertTrue(rendered.contains("s3Code=AccessDenied"));
        assertTrue(rendered.contains("httpStatus=403"));
        assertTrue(rendered.contains("requestId=minio-request-123"));
        assertTrue(rendered.contains("permission denied"));
        assertFalse(rendered.contains("private-"));
        assertFalse(rendered.contains("storage.invalid"));
    }

    @Test
    public void diagnosticsDoesNotCloseOrReadTheOriginalHttpResponse() {
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean read = new AtomicBoolean();
        ResponseBody body = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.get("application/xml");
            }

            @Override
            public long contentLength() {
                return 0;
            }

            @Override
            public BufferedSource source() {
                read.set(true);
                return new Buffer();
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://storage.invalid").build())
                .protocol(Protocol.HTTP_1_1).code(503).message("Unavailable").body(body).build();
        ErrorResponse error = new ErrorResponse("ServiceUnavailable", "unavailable",
                "test-bucket", "file.bin", "/file.bin", "test-request", "host-id");

        try {
            OssExceptionDiagnostics.sanitized(new ErrorResponseException(error, response, null));
            assertFalse(closed.get());
            assertFalse(read.get());
        } finally {
            response.close();
        }
    }

    @Test
    public void redactsUrlsHeadersStructuredCredentialsAndStandaloneTokens() {
        String[] messages = {
                "failed https://user:private-url-pass@storage.invalid/a?X-Amz-Signature=private-signature",
                "failed jdbc:mysql://user:private-db-pass@database.invalid/schema",
                "Authorization: AWS4-HMAC-SHA256 Credential=private-access, Signature=private-signature",
                "Cookie: sid=private-cookie; other=private-other",
                "{\"secretKey\":\"private-secret\", \"password\":\"private-password\"}",
                "oss.access-key=private-access",
                "aws_secret_access_key=private-aws-secret",
                "<SecretAccessKey>private-xml-secret</SecretAccessKey>",
                "failed Bearer private-bearer",
                "failed Basic private-basic"
        };
        for (String message : messages) {
            String rendered = render(OssExceptionDiagnostics.sanitized(new RuntimeException(message)));
            assertFalse("Credential leaked", rendered.contains("private-"));
        }
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.test_signature";
        assertFalse(render(OssExceptionDiagnostics.sanitized(new RuntimeException(token))).contains(token));
    }

    @Test
    public void sanitizesEveryLevelAndRemovesControlCharacters() {
        RuntimeException root = new RuntimeException("root\r\nAuthorization: Bearer private-root");
        RuntimeException original = new RuntimeException("wrapper", root);
        original.addSuppressed(new RuntimeException("cleanup password=private-cleanup"));

        Throwable safe = OssExceptionDiagnostics.sanitized(original);

        assertFalse(render(safe).contains("private-"));
        assertFalse(safe.getCause().getMessage().contains("\n"));
        assertFalse(safe.getCause().getMessage().contains("\r"));
    }

    @Test
    public void boundsCircularAndVeryDeepChains() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);
        assertTrue(render(OssExceptionDiagnostics.sanitized(first)).contains("circular"));

        Throwable chain = new RuntimeException("root");
        for (int i = 0; i < 100; i++) chain = new RuntimeException("wrapper", chain);
        assertTrue(render(OssExceptionDiagnostics.sanitized(chain)).contains("truncated"));
    }

    @Test
    public void boundsOversizedMessagesAndStackTraces() {
        RuntimeException original = new RuntimeException("x".repeat(20000));
        StackTraceElement[] frames = new StackTraceElement[200];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("Test", "method", "Test.java", i);
        }
        original.setStackTrace(frames);

        Throwable safe = OssExceptionDiagnostics.sanitized(original);

        assertTrue(safe.getMessage().contains("oversized message omitted"));
        assertEquals(128, safe.getStackTrace().length);
        assertEquals(200, original.getStackTrace().length);
    }

    private static String render(Throwable error) {
        StringWriter output = new StringWriter();
        error.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
