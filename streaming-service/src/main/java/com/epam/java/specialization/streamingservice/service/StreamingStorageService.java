package com.epam.java.specialization.streamingservice.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class StreamingStorageService {

    private final MinioClient minioClient;

    @Value("${storage.s3.bucket-name}")
    private String bucketName;

    public StreamingStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public static String masterKey(Long trackId) {
        return trackId + "/master";
    }

    public static String coverKey(Long trackId) {
        return trackId + "/cover";
    }

    public void putBytes(String key, byte[] data, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(key)
                        .stream(new java.io.ByteArrayInputStream(data), data.length, -1)
                        .contentType(contentType)
                        .build());
    }

    /** Content type of a stored object, or {@code null} when it does not exist. */
    public String contentTypeOrNull(String key) {
        try {
            return minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(key).build())
                    .contentType();
        } catch (Exception e) {
            return null;
        }
    }

    /** Public path of the stored cover (with a version so browsers refetch after a replacement), or {@code null}. */
    public String coverUrlOrNull(Long trackId) {
        try {
            var stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(coverKey(trackId)).build());
            return "/api/v1/media/" + trackId + "/cover?v=" + stat.lastModified().toEpochSecond();
        } catch (Exception e) {
            return null;
        }
    }

    public void uploadAudio(String fileName, MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType() != null ? file.getContentType() : "audio/mpeg")
                            .build()
            );
        }
    }

    public long objectSize(String fileName) throws Exception {
        return minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        ).size();
    }

    public String storageUrl(String fileName) {
        return "s3://" + bucketName + "/" + fileName;
    }

    /** {@code length} bytes from {@code offset} on (a byte range of the object). */
    public InputStream getAudioStream(String fileName, long offset, long length) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .offset(offset)
                        .length(length)
                        .build()
        );
    }

    public InputStream getAudioStream(String fileName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );
    }
}