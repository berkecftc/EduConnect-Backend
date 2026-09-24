package com.educonnect.authservices.service;

import io.minio.*;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

    private final StorageUrls storageUrls;
    private final UploadValidator uploadValidator;

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
            throw new RuntimeException("Error initializing Minio client", e);
        }
    }

    /**
     * Bucket'ı kontrol eder, yoksa oluşturur.
     */
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
                log.info("MinIO bucket created: {}", bucketName);
            }

            minioClient.deleteBucketPolicy(
                    DeleteBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            log.info("MinIO bucket '{}' is private (public policy removed)", bucketName);

        } catch (Exception e) {
            throw new RuntimeException("Error checking/creating MinIO bucket: " + e.getMessage(), e);
        }
    }

    public String createPresignedUrl(String storedUrl) {
        String objectName = extractObjectName(storedUrl);
        if (objectName == null) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Could not create presigned URL for object {}", objectName, e);
            return null;
        }
    }

    /**
     * Akademisyen kimlik kartı fotoğrafını MinIO'ya yükler ve TAM URL döner.
     */
    public String uploadIdCardImage(MultipartFile file, UUID userId) {
        ValidatedUpload upload = uploadValidator.validate(file, UploadKind.DOCUMENT);
        String objectName = "id-cards/" + userId + extensionOrDefault(upload);
        try {

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(upload.contentType())
                            .build()
            );

            return storageUrls.url(bucketName, objectName);

        } catch (Exception e) {
            throw new RuntimeException("Error uploading ID card image to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * MinIO'dan kimlik kartı fotoğrafını siler.
     * @param imageUrl Silinecek fotoğrafın tam URL'si
     */
    public void deleteIdCardImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            // URL'den object name'i çıkar: http://localhost:9000/bucket/id-cards/uuid.jpg -> id-cards/uuid.jpg
            String objectName = extractObjectName(imageUrl);
            if (objectName != null) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                );
                System.out.println("Auth Service: Kimlik kartı fotoğrafı silindi -> " + objectName);
            }
        } catch (Exception e) {
            System.err.println("Error deleting ID card image from MinIO: " + e.getMessage());
            // Silme hatası kritik değil, işlemi durdurmuyoruz
        }
    }

    /**
     * URL'den object name'i çıkarır.
     */
    private String extractObjectName(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return storageUrls.objectName(imageUrl, bucketName);
    }

    private static String extensionOrDefault(ValidatedUpload upload) {
        return upload.extension().isEmpty() ? ".jpg" : upload.extension();
    }

    /**
     * Öğrenci belgesini MinIO'ya yükler ve TAM URL döner.
     */
    public String uploadStudentDocument(MultipartFile file, UUID userId) {
        ValidatedUpload upload = uploadValidator.validate(file, UploadKind.DOCUMENT);
        String objectName = "student-documents/" + userId + extensionOrDefault(upload);
        try {

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(upload.contentType())
                            .build()
            );

            return storageUrls.url(bucketName, objectName);

        } catch (Exception e) {
            throw new RuntimeException("Error uploading student document to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * MinIO'dan öğrenci belgesini siler.
     * @param documentUrl Silinecek belgenin tam URL'si
     */
    public void deleteStudentDocument(String documentUrl) {
        if (documentUrl == null || documentUrl.isEmpty()) {
            return;
        }
        try {
            String objectName = extractObjectName(documentUrl);
            if (objectName != null) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                );
                System.out.println("Auth Service: Öğrenci belgesi silindi -> " + objectName);
            }
        } catch (Exception e) {
            System.err.println("Error deleting student document from MinIO: " + e.getMessage());
        }
    }
}

