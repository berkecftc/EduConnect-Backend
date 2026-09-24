package com.educonnect.userservice.service;

import io.minio.*; // Tüm MinIO sınıflarını import ediyoruz (SetBucketPolicyArgs dahil)
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

    private final StorageUrls storageUrls;
    private final UploadValidator uploadValidator;

    // MinioClient'ı yapılandırma ayarlarıyla başlat
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

            // Bucket'ın var olup olmadığını kontrol et ve yoksa oluştur (+ Public Yap)
            ensureBucketExists();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing Minio client", e);
        }
    }

    /**
     * Bucket'ı kontrol eder, yoksa oluşturur ve HERKESE AÇIK (Public) yapar.
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
                System.out.println("User Service: MinIO bucket oluşturuldu -> " + bucketName);
            }

            // 🔥 KRİTİK KISIM: Bucket politikasını "Public Read" olarak ayarla.
            // Bu sayede Access Denied hatası almadan resimler görüntülenir.
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

            System.out.println("User Service: Bucket politikası 'Public Read' olarak güncellendi.");

        } catch (Exception e) {
            throw new RuntimeException("Error checking/creating MinIO bucket: " + e.getMessage(), e);
        }
    }

    /**
     * Dosyayı MinIO'ya yükler ve TAM URL döner.
     */
    public String uploadFile(MultipartFile file, UUID userId) {
        ValidatedUpload upload = uploadValidator.validate(file, UploadKind.IMAGE);
        String objectName = "profiles/" + userId + (upload.extension().isEmpty() ? ".jpg" : upload.extension());
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
            throw new RuntimeException("Error uploading file to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Presigned URL oluşturur. (Bucket Public olduğu için buna aslında gerek kalmadı
     * ama özel durumlarda kullanmak istersen kalabilir).
     */
    public String getFileUrl(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error getting file URL from MinIO: " + e.getMessage(), e);
        }
    }

}