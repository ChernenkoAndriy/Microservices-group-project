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

    public InputStream getAudioStream(String fileName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );
    }
}