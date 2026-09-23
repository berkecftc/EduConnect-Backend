package com.educonnect.authservices.service;

import io.minio.*;
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

    private String minioUrl;

    public MinioService(@Value("${minio.url}") String url,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name}") String bucketName) {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(url)
                    .credentials(accessKey, secretKey)
                    .build();
            this.bucketName = bucketName;
            this.minioUrl = url;

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
        try {
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String objectName = "id-cards/" + userId.toString() + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // Tam URL döner: http://localhost:9000/academician-id-cards/id-cards/uuid.jpg
            return minioUrl + "/" + bucketName + "/" + objectName;

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
        if (imageUrl == null) return null;
        // URL formatı: http://localhost:9000/bucket-name/id-cards/uuid.jpg
        String bucketPath = "/" + bucketName + "/";
        int bucketIndex = imageUrl.indexOf(bucketPath);
        if (bucketIndex > 0) {
            return imageUrl.substring(bucketIndex + bucketPath.length());
        }
        return null;
    }

    /**
     * Dosya uzantısını alır.
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".jpg";
        }
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex > 0 ? filename.substring(dotIndex) : ".jpg";
    }

    /**
     * Öğrenci belgesini MinIO'ya yükler ve TAM URL döner.
     */
    public String uploadStudentDocument(MultipartFile file, UUID userId) {
        try {
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String objectName = "student-documents/" + userId.toString() + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // Tam URL döner: http://localhost:9000/bucket/student-documents/uuid.pdf
            return minioUrl + "/" + bucketName + "/" + objectName;

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

