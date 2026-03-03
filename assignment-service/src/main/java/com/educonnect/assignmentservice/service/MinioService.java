package com.educonnect.assignmentservice.service;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {
    private final MinioClient minioClient;
    @Value("${minio.bucket-name}") private String bucketName;

    public MinioService(MinioClient minioClient) { this.minioClient = minioClient; }

    public String uploadFile(MultipartFile file) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return "http://localhost:9000/" + bucketName + "/" + fileName;
        } catch (Exception e) {
            throw new RuntimeException("Dosya yüklenemedi: " + e.getMessage());
        }
    }

    /**
     * MinIO'dan dosya indirir.
     */
    public InputStream downloadFile(String fileUrl) {
        try {
            String objectName = extractObjectName(fileUrl);
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Dosya indirilemedi: " + e.getMessage());
        }
    }

    /**
     * URL'den dosya adını (object name) çıkarır.
     */
    public String extractObjectName(String fileUrl) {
        String prefix = "http://localhost:9000/" + bucketName + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        return fileUrl;
    }

    /**
     * URL'den orijinal dosya adını çıkarır (UUID prefix'i kaldırarak).
     */
    public String extractOriginalFileName(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        int underscoreIndex = objectName.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < objectName.length() - 1) {
            return objectName.substring(underscoreIndex + 1);
        }
        return objectName;
    }
}