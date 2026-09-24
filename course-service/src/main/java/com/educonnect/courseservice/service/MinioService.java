package com.educonnect.courseservice.service;

import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {
    private final MinioClient minioClient;
    private final StorageUrls storageUrls;
    private final UploadValidator uploadValidator;
    @Value("${minio.bucket-name}") private String bucketName;

    public MinioService(MinioClient minioClient, StorageUrls storageUrls, UploadValidator uploadValidator) {
        this.minioClient = minioClient;
        this.storageUrls = storageUrls;
        this.uploadValidator = uploadValidator;
    }

    public String uploadFile(MultipartFile file) {
        ValidatedUpload upload = uploadValidator.validate(file, UploadKind.IMAGE);
        String fileName = UUID.randomUUID() + "_" + upload.safeOriginalName();
        try (InputStream inputStream = file.getInputStream()) {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(upload.contentType())
                    .build());

            return storageUrls.url(bucketName, fileName);
        } catch (Exception e) {
            throw new RuntimeException("MinIO Yükleme Hatası: " + e.getMessage());
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
            throw new RuntimeException("MinIO İndirme Hatası: " + e.getMessage());
        }
    }

    public String extractObjectName(String fileUrl) {
        return storageUrls.objectName(fileUrl, bucketName);
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
