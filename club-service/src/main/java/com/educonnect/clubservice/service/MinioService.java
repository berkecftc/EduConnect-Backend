package com.educonnect.clubservice.service;

import com.educonnect.common.storage.PresignedUrls;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final PresignedUrls presignedUrls;
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
            this.presignedUrls = new PresignedUrls(storageUrls, accessKey, secretKey);
            this.bucketName = bucketName;
            this.storageUrls = storageUrls;
            this.uploadValidator = uploadValidator;
            ensureBucketExists();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing Minio client", e);
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
                System.out.println("MinIO bucket oluşturuldu: " + bucketName);
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

            System.out.println("Bucket politikası güncellendi (Public Read): " + bucketName);

        } catch (Exception e) {
            throw new RuntimeException("Error checking/creating MinIO bucket: " + e.getMessage(), e);
        }
    }

    public String uploadFile(MultipartFile file, UUID userId) {
        return uploadFile(file, "profiles", userId.toString());
    }

    public String uploadFile(MultipartFile file, String folder, String nameBase) {
        ValidatedUpload upload = uploadValidator.validate(file, UploadKind.IMAGE);
        String extension = upload.extension().isEmpty() ? ".jpg" : upload.extension();
        String safeFolder = (folder == null || folder.isBlank()) ? "misc" : folder.replaceAll("[^A-Za-z0-9_-]", "");
        String safeNameBase = (nameBase == null || nameBase.isBlank())
                ? UUID.randomUUID().toString()
                : nameBase.replaceAll("[^A-Za-z0-9_-]", "");
        String objectName = safeFolder + "/" + safeNameBase + extension;

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
            throw new RuntimeException("Error uploading file to MinIO (custom folder): " + e.getMessage(), e);
        }
    }

    public String getFileUrl(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        try {
            return presignedUrls.get(bucketName, storageUrls.objectName(objectName, bucketName), Duration.ofDays(7));
        } catch (RuntimeException e) {
            throw new RuntimeException("Error getting file URL from MinIO: " + e.getMessage(), e);
        }
    }
}
