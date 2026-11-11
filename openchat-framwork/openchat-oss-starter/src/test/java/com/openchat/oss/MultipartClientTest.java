package com.openchat.oss;

import io.minio.MinioAsyncClient;
import io.minio.errors.ErrorResponseException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/** 使用内存 HTTP 响应执行真实 SDK 请求构建与 XML 解析，不连接外部存储。 */
public class MultipartClientTest {
    private static final String BUCKET = "upload-test";
    private static final String OBJECT = "file/2026-10/123/file name+one.bin";
    private static final String UPLOAD_ID = "test-upload+id/one";
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private OkHttpClient httpClient;
    private MultipartClient client;
    private volatile int responseCode = 200;
    private volatile String responseXml;

    @Before
    public void setUp() {
        responseXml = partsXml(false, "");
        httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            requests.add(request);
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("Test response")
                    .header("Content-Type", "application/xml")
                    .body(ResponseBody.create(responseXml, MediaType.get("application/xml")))
                    .build();
        }).build();
        client = new MultipartClient(MinioAsyncClient.builder()
                .endpoint("http://127.0.0.1:1")
                .region("us-east-1")
                .credentials("test-access-key", "test-secret-key")
                .httpClient(httpClient)
                .build());
    }

    @After
    public void tearDown() {
        client.close();
        httpClient.dispatcher().executorService().shutdownNow();
        httpClient.connectionPool().evictAll();
    }

    @Test
    public void listsFromFirstPartWithExactObjectAndUploadId() {
        responseXml = partsXml(false, partXml(1, "first-etag", 10377065));

        var parts = client.parts(BUCKET, "us-east-1", OBJECT, UPLOAD_ID);

        assertEquals(1, parts.size());
        assertEquals(1, parts.getFirst().partNumber());
        assertEquals("first-etag", parts.getFirst().etag());
        assertEquals(10377065L, parts.getFirst().partSize());
        assertEquals(1, requests.size());
        Request request = requests.getFirst();
        assertEquals("GET", request.method());
        assertEquals("/upload-test/file/2026-10/123/file%20name%2Bone.bin",
                request.url().encodedPath());
        assertEquals(UPLOAD_ID, request.url().queryParameter("uploadId"));
        assertEquals("1000", request.url().queryParameter("max-parts"));
        assertNull(request.url().queryParameter("part-number-marker"));
    }

    @Test
    public void returnsEmptyListBeforeAnyPartHasBeenUploaded() {
        assertTrue(client.parts(BUCKET, null, OBJECT, UPLOAD_ID).isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    public void repeatedResumeQueriesKeepStoredPartMetadata() {
        responseXml = partsXml(false, partXml(1, "first-etag", 16777216)
                + partXml(2, "last-etag", 13));

        var first = client.parts(BUCKET, "us-east-1", OBJECT, UPLOAD_ID);
        var second = client.parts(BUCKET, "us-east-1", OBJECT, UPLOAD_ID);

        assertEquals(2, requests.size());
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals(first.getFirst().etag(), second.getFirst().etag());
        assertEquals(2, second.getLast().partNumber());
        assertEquals("last-etag", second.getLast().etag());
        assertEquals(13L, second.getLast().partSize());
    }

    @Test
    public void differentObjectsDoNotShareRequestPaths() {
        client.parts(BUCKET, "us-east-1", "file/first.bin", UPLOAD_ID);
        client.parts(BUCKET, "us-east-1", "file/second.bin", "second-upload");

        assertEquals("/upload-test/file/first.bin", requests.getFirst().url().encodedPath());
        assertEquals("/upload-test/file/second.bin", requests.getLast().url().encodedPath());
        assertEquals("second-upload", requests.getLast().url().queryParameter("uploadId"));
    }

    @Test
    public void rejectsTruncatedListInsteadOfCompletingWithMissingParts() {
        responseXml = partsXml(true, partXml(1, "first-etag", 16777216));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.parts(BUCKET, "us-east-1", OBJECT, UPLOAD_ID));

        assertEquals("Too many upload parts", error.getMessage());
    }

    @Test
    public void preservesNoSuchUploadForSessionRecovery() {
        responseCode = 404;
        responseXml = errorXml("NoSuchUpload");

        ErrorResponseException error = assertThrows(ErrorResponseException.class,
                () -> client.parts(BUCKET, "us-east-1", OBJECT, UPLOAD_ID));

        assertEquals("NoSuchUpload", error.errorResponse().code());
    }

    @Test
    public void preservesAccessDeniedInsteadOfTreatingItAsMissingUpload() {
        responseCode = 403;
        responseXml = errorXml("AccessDenied");

        ErrorResponseException error = assertThrows(ErrorResponseException.class,
                () -> client.parts(BUCKET, "us-east-1", OBJECT, UPLOAD_ID));

        assertEquals("AccessDenied", error.errorResponse().code());
    }

    @Test
    public void rejectsInvalidObjectBeforeSendingRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> client.parts(BUCKET, "us-east-1", "", UPLOAD_ID));
        assertThrows(IllegalArgumentException.class,
                () -> client.parts(BUCKET, "us-east-1", "file/../other.bin", UPLOAD_ID));
        assertTrue(requests.isEmpty());
    }

    private static String partsXml(boolean truncated, String parts) {
        return "<ListPartsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                + "<Bucket>upload-test</Bucket><Key>file.bin</Key><UploadId>test-upload</UploadId>"
                + "<Initiator><ID>test-user</ID><DisplayName>Test</DisplayName></Initiator>"
                + "<Owner><ID>test-user</ID><DisplayName>Test</DisplayName></Owner>"
                + "<StorageClass>STANDARD</StorageClass>"
                + "<PartNumberMarker>0</PartNumberMarker><NextPartNumberMarker>2</NextPartNumberMarker>"
                + "<MaxParts>1000</MaxParts><IsTruncated>" + truncated + "</IsTruncated>"
                + parts + "</ListPartsResult>";
    }

    private static String partXml(int number, String etag, long size) {
        return "<Part><PartNumber>" + number + "</PartNumber>"
                + "<LastModified>2026-10-04T00:00:00.000Z</LastModified>"
                + "<ETag>\"" + etag + "\"</ETag><Size>" + size + "</Size></Part>";
    }

    private static String errorXml(String code) {
        return "<Error><Code>" + code + "</Code><Message>Test error</Message>"
                + "<BucketName>upload-test</BucketName><Key>file.bin</Key>"
                + "<RequestId>test-request</RequestId><HostId>test-host</HostId></Error>";
    }
}
