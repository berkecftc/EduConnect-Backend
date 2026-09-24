package com.educonnect.assignmentservice.service;

import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.DeleteBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {
    private final MinioClient minioClient;
    private final String bucketName;
    private final StorageUrls storageUrls;
    private final UploadValidator uploadValidator;

    public MinioService(@Value("${minio.url}") String minioUrl,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name:${minio.bucket-name}}") String bucketName,
                        StorageUrls storageUrls,
                        UploadValidator uploadValidator) {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(minioUrl)
                    .credentials(accessKey, secretKey)
                    .build();
            this.bucketName = bucketName;
            this.storageUrls = storageUrls;
            this.uploadValidator = uploadValidator;
            ensureBucketExists();
        } catch (Exception e) {
            throw new RuntimeException("MinIO client baslatilamadi", e);
        }
    }

    public String uploadFile(MultipartFile file) {
        ValidatedUpload upload = uploadValidator.validate(file, UploadKind.ATTACHMENT);
        String objectName = UUID.randomUUID() + "_" + upload.safeOriginalName();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(upload.contentType())
                    .build());

            return storageUrls.url(bucketName, objectName);
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

            minioClient.deleteBucketPolicy(
                    DeleteBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket kontrol/oluşturma hatası: " + e.getMessage(), e);
        }
    }

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

    public String extractObjectName(String fileUrl) {
        return storageUrls.objectName(fileUrl, bucketName);
    }

    public String normalizeToFullUrl(String fileUrlOrObjectName) {
        if (fileUrlOrObjectName == null || fileUrlOrObjectName.isBlank()) {
            return fileUrlOrObjectName;
        }
        String path = storageUrls.toStoredValue(fileUrlOrObjectName);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return storageUrls.url(bucketName, storageUrls.objectName(path, bucketName));
    }

    public String extractOriginalFileName(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        int underscoreIndex = objectName.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < objectName.length() - 1) {
            return objectName.substring(underscoreIndex + 1);
        }
        return objectName;
    }
}
