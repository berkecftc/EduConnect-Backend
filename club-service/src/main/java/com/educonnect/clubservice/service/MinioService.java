package com.educonnect.clubservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import io.minio.SetBucketPolicyArgs;
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

    // 👇 EKLENDİ: URL'i sınıf içinde tutmamız lazım
    private String minioUrl;

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
            this.minioUrl = url; // 👇 EKLENDİ: URL değişkenini kaydettik

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

            // Eğer bucket yoksa oluştur
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                System.out.println("MinIO bucket oluşturuldu: " + bucketName);
            }

            // 🔥 DEĞİŞİKLİK BURADA 🔥
            // "if (!exists)" bloğunun DIŞINA çıktık.
            // Bucket eskiden oluşmuş olsa bile, her başlatmada "Public" ayarını zorla yapıştırıyoruz.

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

    /**
     * Dosyayı MinIO'ya yükler ve TAM URL döner.
     */
    public String uploadFile(MultipartFile file, UUID userId) {
        try {
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String objectName = "profiles/" + userId.toString() + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 👇 DEĞİŞTİRİLDİ: Artık tam link dönüyor
            return minioUrl + "/" + bucketName + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("Error uploading file to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Dosyayı belirtilen klasöre yükler ve TAM URL döner.
     * (Logolar için burası kullanılıyor)
     */
    public String uploadFile(MultipartFile file, String folder, String nameBase) {
        try {
            String fileExtension = getFileExtension(file.getOriginalFilename());

            String safeFolder = (folder == null || folder.isBlank()) ? "misc" : folder.replaceAll("^/+|/+$", "");
            String safeNameBase = (nameBase == null || nameBase.isBlank()) ? UUID.randomUUID().toString() : nameBase;
            String objectName = safeFolder + "/" + safeNameBase + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 👇 DEĞİŞTİRİLDİ: Artık tam link dönüyor
            // Örnek Çıktı: http://localhost:9000/educonnect-bucket/logos/abc-123.png
            return minioUrl + "/" + bucketName + "/" + objectName;

        } catch (Exception e) {
            throw new RuntimeException("Error uploading file to MinIO (custom folder): " + e.getMessage(), e);
        }
    }

    /**
     * Presigned URL (Süreli Erişim) - Gerekirse kullanılır
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

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}