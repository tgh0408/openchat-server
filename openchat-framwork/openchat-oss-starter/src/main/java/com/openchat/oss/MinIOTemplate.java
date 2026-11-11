package com.openchat.oss;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import com.openchat.oss.domain.OssReq;
import com.openchat.oss.domain.OssResp;
import io.minio.*;
import io.minio.errors.*;
import io.minio.Http.Method;
import io.minio.messages.Item;
import io.minio.messages.ListAllMyBucketsResult.Bucket;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@AllArgsConstructor
public class MinIOTemplate {

    /**
     * MinIO 客户端
     */
    MinioClient minioClient;

    /**
     * MinIO 配置类
     */
    OssProperties ossProperties;

    /** 客户端 S3 预签名入口，供 PUT 上传和 GET 下载使用。 */
    MinioClient uploadClient;

    /** 保留未拆分内外网入口的现有构造方式。 */
    public MinIOTemplate(MinioClient minioClient, OssProperties ossProperties) {
        this(minioClient, ossProperties, minioClient);
    }

    /** 为已通过业务权限校验的桶内对象生成 GET 签名，不替换签名后的域名或路径。 */
    @SneakyThrows
    public String getPresignedDownloadUrl(String bucketName, String objectName, int expiresIn) {
        return uploadClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucketName).object(objectName).expiry(expiresIn).build());
    }

    /**
     * 查询所有存储桶
     *
     * @return Bucket 集合
     */
    @SneakyThrows
    public List<Bucket> listBuckets() {
        return minioClient.listBuckets();
    }

    /**
     * 桶是否存在
     *
     * @param bucketName 桶名
     * @return 是否存在
     */
    @SneakyThrows
    public boolean bucketExists(String bucketName) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void makeBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 删除一个空桶 如果存储桶存在对象不为空时，删除会报错。
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void removeBucket(String bucketName) {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }

    /**
     * 返回临时带签名、过期时间一天、PUT请求方式的访问URL
     */
    @SneakyThrows
    public OssResp getPreSignedObjectUrl(OssReq req) {
        return getPreSignedObjectUrl(req, ossProperties.getBucketName());
    }

    @SneakyThrows
    public OssResp getPreSignedObjectUrl(OssReq req, String bucketName) {
        String absolutePath;
        String fileName = null;
        if (req.isAutoPath()) {
            Map<String, String> info = generateAutoPath(req);
            absolutePath = info.get("path");
            fileName = info.get("fileName");
        } else {
            absolutePath = req.getFilePath() + StrUtil.SLASH + req.getFileName();
        }

        String url = uploadClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucketName)
                        .object(absolutePath)
                        .expiry(60 * 60 * 24)
                        .build());
        return OssResp.builder()
                .uploadUrl(url)
                .downloadUrl(getDownloadUrl(bucketName, absolutePath))
                .fileName(fileName)
                .build();
    }

    /**
     * 为 Multipart 分片生成短期签名 URL，不经业务服务中转视频字节。
     */
    @SneakyThrows
    public String getPresignedMultipartUrl(String objectPath, Method method,
                                           Map<String, String> queryParams) {
        return getPresignedMultipartUrl(objectPath, method, queryParams, ossProperties.getBucketName());
    }

    @SneakyThrows
    public String getPresignedMultipartUrl(String objectPath, Method method,
                                           Map<String, String> queryParams, String bucketName) {
        return uploadClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(method)
                        .bucket(bucketName)
                        .object(objectPath)
                        .expiry(15 * 60)
                        .extraQueryParams(queryParams)
                        .build());
    }

    private String getDownloadUrl(String bucket, String pathFile) {
        return StrUtil.SLASH + bucket + StrUtil.SLASH + pathFile;
    }

    /** 查询默认桶对象元数据，不根据客户端 URL 发起网络请求。 */
    @SneakyThrows
    public StatObjectResponse statObject(String objectName) {
        return statObject(ossProperties.getBucketName(), objectName);
    }

    @SneakyThrows
    public StatObjectResponse statObject(String bucketName, String objectName) {
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName).object(objectName).build());
    }

    /** 删除默认桶的一个对象；不存在视为成功，其他错误交由可靠调用重试。 */
    @SneakyThrows
    public void removeObject(String objectName) {
        removeObject(ossProperties.getBucketName(), objectName);
    }

    @SneakyThrows
    public void removeObject(String bucketName, String objectName) {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName).object(objectName).build());
    }

    /** 写入已校验的有界内容，显式设置对象类型和精确长度。 */
    @SneakyThrows
    public void putObject(String objectName, byte[] bytes, String contentType) {
        putObject(ossProperties.getBucketName(), objectName, bytes, contentType);
    }

    @SneakyThrows
    public void putObject(String bucketName, String objectName, byte[] bytes, String contentType) {
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName).object(objectName)
                    .contentType(contentType).stream(stream, (long) bytes.length, -1L).build());
        }
    }

    /** 按已校验的精确长度流式上传对象，避免将大文件一次性读入内存。 */
    @SneakyThrows
    public void putObject(String objectName, InputStream inputStream, long size, String contentType) {
        putObject(ossProperties.getBucketName(), objectName, inputStream, size, contentType);
    }

    @SneakyThrows
    public void putObject(String bucketName, String objectName, InputStream inputStream, long size,
                          String contentType) {
        if (inputStream == null || size < 0) {
            throw new IllegalArgumentException("Invalid object stream");
        }
        PutObjectArgs.Builder builder = PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(inputStream, size, -1L);
        if (StrUtil.isNotBlank(contentType)) {
            builder.contentType(contentType);
        }
        minioClient.putObject(builder.build());
    }

    /** 只读取文件头用于格式校验，避免将整个上传对象载入服务端内存。 */
    @SneakyThrows
    public byte[] getObjectHeader(String objectName) {
        return getObjectHeader(ossProperties.getBucketName(), objectName, 32);
    }

    /** 读取有界格式头；语音 PCM 校验需要完整的 44 字节头。 */
    @SneakyThrows
    public byte[] getObjectHeader(String objectName, int length) {
        return getObjectHeader(ossProperties.getBucketName(), objectName, length);
    }

    @SneakyThrows
    public byte[] getObjectHeader(String bucketName, String objectName, int length) {
        if (length < 1 || length > 4096) throw new IllegalArgumentException("Invalid header length");
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName).object(objectName).offset(0L).length((long) length).build())) {
            return stream.readNBytes(length);
        }
    }

    /**
     * GetObject接口用于获取某个文件（Object）。此操作需要对此Object具有读权限。
     *
     * @param bucketName  桶名
     * @param ossFilePath Oss文件路径
     */
    @SneakyThrows
    public byte[] getObject(String bucketName, String ossFilePath) {
        if (bucketName == null) {
            bucketName = ossProperties.getBucketName();
        }
        if (ossFilePath == null) return null;
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(ossFilePath)
                        .build())){
            return IOUtils.toByteArray(stream);
        }
    }

    /**
     * Copy object
     * 拷贝指定路径文件到另一个目录下
     */

    public void copyObject(String bucketName, String path, String targetBucketName, String targetPath) throws MinioException, ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .source(SourceObject.builder()
                                .bucket(bucketName)
                                .object(path)
                                .build()
                        )
                        .bucket(targetBucketName)
                        .object(targetPath)
                        .build());
    }


    /**
     * 查询桶的对象信息
     *
     * @param bucketName 桶名
     * @param recursive  是否递归查询
     * @return
     */
    @SneakyThrows
    public Iterable<Result<Item>> listObjects(String bucketName, boolean recursive) {
        return minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(recursive).build());
    }

    /**
     * 生成随机文件名，防止重复；仅在提供 uid 时添加 ID 目录。
     */
    public Map<String, String> generateAutoPath(OssReq req) {
        cn.hutool.core.lang.UUID uuid = cn.hutool.core.lang.UUID.fastUUID();
        String suffix = FileNameUtil.getSuffix(req.getFileName());
        String yearAndMonth = DateUtil.format(new Date(), DatePattern.NORM_MONTH_PATTERN);
        String directory = req.getFilePath() + StrUtil.SLASH + yearAndMonth;
        if (req.getUid() != null) {
            directory += StrUtil.SLASH + req.getUid();
        }
        Map<String, String> map = new HashMap<>();
        map.put("path", directory + StrUtil.SLASH + uuid + StrUtil.DOT + suffix);
        map.put("fileName", uuid + StrUtil.DOT + suffix);
        return map;
    }

    /**
     * 获取带签名的临时上传元数据对象，前端可获取后，直接上传到Minio
     *
     * @param bucketName
     * @param fileName
     * @return
     */
    @SneakyThrows
    public Map<String, String> getPreSignedPostFormData(String bucketName, String fileName) {
        // 为存储桶创建一个上传策略，过期时间为7天
        PostPolicy policy = new PostPolicy(bucketName, ZonedDateTime.now().plusDays(7));
        // 设置一个参数key，值为上传对象的名称
        policy.addEqualsCondition("key", fileName);
        // 添加Content-Type以"image/"开头，表示只能上传照片
        policy.addStartsWithCondition("Content-Type", "image/");
        // 设置上传文件的大小 64kiB to 10MiB.
        policy.addContentLengthRangeCondition(64 * 1024, 10 * 1024 * 1024);
        return minioClient.getPresignedPostFormData(policy);
    }

    /**
     * 服务器上传文件
     *
     * @param filePath 文件在服务器的路径及名字
     */
    public String upLoadFile(String bucketName, String filePath, InputStream inputStream) throws MinioException, IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (bucketName == null || bucketName.isEmpty()) {
            bucketName = ossProperties.getBucketName();
        }
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .stream(inputStream, (long) inputStream.available(), -1L)
                        .build()
        );
        return StrUtil.SLASH + bucketName + filePath;
    }

}
