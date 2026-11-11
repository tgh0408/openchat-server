package com.openchat.oss;

import io.minio.AbortMultipartUploadArgs;
import io.minio.CompleteMultipartUploadArgs;
import io.minio.CreateMultipartUploadArgs;
import io.minio.Http;
import io.minio.ListPartsArgs;
import io.minio.MinioAsyncClient;
import io.minio.StatObjectArgs;
import io.minio.messages.ListPartsResult;
import io.minio.messages.Part;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Encapsulates the MinIO multipart API used by the upload workflow. */
public class MultipartClient extends MinioAsyncClient {
    public MultipartClient(MinioAsyncClient client) {
        super(client);
    }

    @SneakyThrows
    public String initiate(String bucket, String region, String object, Map<String, String> metadata,
                           String contentType) {
        Http.Headers headers = new Http.Headers();
        headers.set(Http.Headers.CONTENT_TYPE, contentType);
        metadata.forEach((key, value) -> headers.set("x-amz-meta-" + key, value));
        return await(createMultipartUpload(CreateMultipartUploadArgs.builder()
                .bucket(bucket)
                .region(region)
                .object(object)
                .headers(headers)
                .build())).result().uploadId();
    }

    @SneakyThrows
    public List<Part> parts(String bucket, String region, String object, String uploadId) {
        // 首次查询不传 marker；9.0.3 拒绝 0，传 1 则会漏掉第一片。
        ListPartsArgs args = ListPartsArgs.builder()
                .bucket(bucket)
                .region(region)
                .uploadId(uploadId)
                .maxParts(1000)
                .build();
        ListPartsResult result = await(listParts(new ObjectListPartsArgs(args, object))).result();
        if (result.isTruncated()) throw new IllegalStateException("Too many upload parts");
        return result.parts();
    }

    /** 兼容 9.0.3 的 ListPartsArgs.Builder 缺少 object 方法，仍复用 SDK 的对象校验与请求流程。 */
    private static final class ObjectListPartsArgs extends ListPartsArgs {
        private final ListPartsArgs delegate;

        private ObjectListPartsArgs(ListPartsArgs delegate, String object) {
            StatObjectArgs validated = StatObjectArgs.builder()
                    .bucket(delegate.bucket()).region(delegate.region()).object(object).build();
            this.bucketName = validated.bucket();
            this.region = validated.region();
            this.objectName = validated.object();
            this.delegate = delegate;
        }

        @Override
        public String uploadId() {
            return delegate.uploadId();
        }

        @Override
        public Integer maxParts() {
            return delegate.maxParts();
        }

        @Override
        public Integer partNumberMarker() {
            return delegate.partNumberMarker();
        }
    }

    @SneakyThrows
    public void complete(String bucket, String region, String object, String uploadId, List<Part> parts) {
        await(completeMultipartUpload(CompleteMultipartUploadArgs.builder()
                .bucket(bucket)
                .region(region)
                .object(object)
                .uploadId(uploadId)
                .parts(parts.stream()
                        .map(part -> new Part(part.partNumber(), part.etag()))
                        .toArray(Part[]::new))
                .build()));
    }

    @SneakyThrows
    public void abort(String bucket, String region, String object, String uploadId) {
        await(abortMultipartUpload(AbortMultipartUploadArgs.builder()
                .bucket(bucket)
                .region(region)
                .object(object)
                .uploadId(uploadId)
                .build()));
    }

    @SneakyThrows
    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            throw error.getCause();
        }
    }
}
