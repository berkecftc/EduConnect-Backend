package com.educonnect.eventservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;
    private final StorageUrls storageUrls;
    private final UploadValidator uploadValidator;

    @Value("${minio.bucket.name}")
    private String bucketName;

    public MinioService(@Value("${minio.url}") String url,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name}") String bucketName,
                        StorageUrls storageUrls,
                        UploadValidator uploadValidator) {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(url)
                    .credentials(accessKey, secretKey)
                    .build();

            this.bucketName = bucketName;
            this.storageUrls = storageUrls;
            this.uploadValidator = uploadValidator;
            ensureBucketExists();

        } catch (Exception e) {
            throw new RuntimeException("Minio client initialization failed", e);
        }
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("MinIO bucket oluşturuldu: {}", bucketName);
            }

            String policyJson = String.format(
                    "{\n" +
                            "    \"Version\": \"2012-10-17\",\n" +
                            "    \"Statement\": [\n" +
                            "        {\n" +
                            "            \"Effect\": \"Allow\",\n" +
                            "            \"Principal\": {\"AWS\": [\"*\"]},\n" +
                            "            \"Action\": [\"s3:GetObject\"],\n" +
                            "            \"Resource\": [\"arn:aws:s3:::%s/*\"]\n" +
                            "        }\n" +
                            "    ]\n" +
                            "}", bucketName);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policyJson)
                            .build()
            );

            log.info("Bucket politikası public read olarak güncellendi: {}", bucketName);

        } catch (Exception e) {
            throw new RuntimeException("Error checking/creating MinIO bucket: " + e.getMessage(), e);
        }
    }

    public ValidatedUpload validateImage(MultipartFile file) {
        return uploadValidator.validate(file, UploadKind.IMAGE);
    }

    public String uploadFile(MultipartFile file, String folder, String fileName) {
        ValidatedUpload upload = validateImage(file);
        String extension = upload.extension().isEmpty() ? ".jpg" : upload.extension();
        String safeFolder = (folder == null || folder.isEmpty()) ? "images" : folder.replaceAll("[^A-Za-z0-9_-]", "");
        String objectName = safeFolder + "/" + fileName.replaceAll("[^A-Za-z0-9_-]", "") + extension;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(upload.contentType())
                            .build()
            );
            return storageUrls.url(bucketName, objectName);

        } catch (Exception e) {
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String fullUrlOrObjectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageUrls.objectName(fullUrlOrObjectName, bucketName))
                            .build()
            );
        } catch (Exception e) {
            log.warn("MinIO'dan dosya silinemedi: {}", e.getMessage());
        }
    }
}
