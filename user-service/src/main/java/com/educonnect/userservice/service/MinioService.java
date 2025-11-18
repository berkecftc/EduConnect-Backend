package com.educonnect.userservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
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

    // MinioClient'ı yapılandırma ayarlarıyla başlat
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

            // Bucket'ın var olup olmadığını kontrol et ve yoksa oluştur
            ensureBucketExists();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing Minio client", e);
        }
    }

    /**
     * Bucket'ın var olup olmadığını kontrol eder ve yoksa oluşturur.
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
                System.out.println("MinIO bucket oluşturuldu: " + bucketName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error checking/creating MinIO bucket: " + e.getMessage(), e);
        }
    }

    /**
     * Dosyayı MinIO'ya yükler ve dosyanın adını döner.
     */
    public String uploadFile(MultipartFile file, UUID userId) {
        try {
            // Dosya adını benzersiz yap (örn: 123e4567-e89b-12d3-a456-426614174000.png)
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String objectName = "profiles/" + userId.toString() + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName) // Dosyanın bucket içindeki adı ve yolu
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return objectName; // Sadece dosya adını (yolunu) döner
        } catch (Exception e) {
            throw new RuntimeException("Error uploading file to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Bir nesne adı için geçici, okunabilir bir URL oluşturur.
     * (Not: Bu, bucket'ınız public değilse gereklidir)
     */
    public String getFileUrl(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        try {
            // 7 gün geçerli bir URL oluştur (veya daha kısa)
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

    // Basit bir dosya uzantısı bulucu
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg"; // Varsayılan
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}