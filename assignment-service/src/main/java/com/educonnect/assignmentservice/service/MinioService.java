package com.educonnect.assignmentservice.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {
    private final MinioClient minioClient;
    private final String bucketName;
    private final String minioUrl;

    public MinioService(@Value("${minio.url}") String minioUrl,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name:${minio.bucket-name}}") String bucketName) {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(minioUrl)
                    .credentials(accessKey, secretKey)
                    .build();
            this.minioUrl = minioUrl;
            this.bucketName = bucketName;
            ensureBucketExists();
        } catch (Exception e) {
            throw new RuntimeException("MinIO client baslatilamadi", e);
        }
    }

    public String uploadFile(MultipartFile file) {
        try {
            ensureBucketExists();

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return buildObjectUrl(fileName);
        } catch (Exception e) {
            throw new RuntimeException("Dosya yüklenemedi: " + e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            String policyJson = String.format(
                    "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}",
                    bucketName
            );

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policyJson)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket kontrol/oluşturma hatası: " + e.getMessage(), e);
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
        String normalizedMinioUrl = minioUrl.endsWith("/") ? minioUrl.substring(0, minioUrl.length() - 1) : minioUrl;
        String prefix = normalizedMinioUrl + "/" + bucketName + "/";
        if (fileUrl != null && fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }

        // Eski localhost formatındaki URL'lerle geriye dönük uyumluluk.
        String legacyPrefix = "http://localhost:9000/" + bucketName + "/";
        if (fileUrl != null && fileUrl.startsWith(legacyPrefix)) {
            return fileUrl.substring(legacyPrefix.length());
        }

        return fileUrl;
    }

    public String normalizeToFullUrl(String fileUrlOrObjectName) {
        if (fileUrlOrObjectName == null || fileUrlOrObjectName.isBlank()) {
            return fileUrlOrObjectName;
        }

        String normalizedMinioUrl = minioUrl.endsWith("/") ? minioUrl.substring(0, minioUrl.length() - 1) : minioUrl;
        String fullPrefix = normalizedMinioUrl + "/" + bucketName + "/";
        if (fileUrlOrObjectName.startsWith(fullPrefix)) {
            return fileUrlOrObjectName;
        }

        // Dış servisten zaten tam URL geldiyse bozma.
        if (fileUrlOrObjectName.startsWith("http://") || fileUrlOrObjectName.startsWith("https://")) {
            return fileUrlOrObjectName;
        }

        String objectName = fileUrlOrObjectName;
        String bucketPrefix = bucketName + "/";
        if (objectName.startsWith(bucketPrefix)) {
            objectName = objectName.substring(bucketPrefix.length());
        }

        return buildObjectUrl(objectName);
    }

    private String buildObjectUrl(String objectName) {
        String normalizedMinioUrl = minioUrl.endsWith("/") ? minioUrl.substring(0, minioUrl.length() - 1) : minioUrl;
        return normalizedMinioUrl + "/" + bucketName + "/" + objectName;
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