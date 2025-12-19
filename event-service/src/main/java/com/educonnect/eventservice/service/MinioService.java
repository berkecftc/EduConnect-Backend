package com.educonnect.eventservice.service;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

    // 👇 EKLENDİ: Tam link oluşturmak için URL'i tutuyoruz
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
            this.minioUrl = url; // URL'i kaydet

            // Başlangıçta bucket kontrolü ve yetki ayarını yap
            ensureBucketExists();

        } catch (Exception e) {
            throw new RuntimeException("Minio client initialization failed", e);
        }
    }

    // 👇 YENİ METOD: Bucket'ı oluşturur ve HERKESE AÇIK (Public) yapar
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
                System.out.println("Event Service: MinIO bucket oluşturuldu -> " + bucketName);
            }

            // 🔥 KRİTİK KISIM: Bucket var olsa bile her açılışta Public yapıyoruz.
            // Bu sayede "Access Denied" hatası asla alınmaz.
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

            System.out.println("Event Service: Bucket politikası 'Public Read' olarak güncellendi.");

        } catch (Exception e) {
            throw new RuntimeException("Error checking/creating MinIO bucket: " + e.getMessage(), e);
        }
    }

    // Dosya Yükleme
    public String uploadFile(MultipartFile file, String folder, String fileName) {
        try {
            // Örn: events/123e4567-....jpg
            String fileExtension = getFileExtension(file.getOriginalFilename());

            // Eğer folder null ise varsayılan bir klasör kullan
            String safeFolder = (folder == null || folder.isEmpty()) ? "images" : folder;
            String objectName = safeFolder + "/" + fileName + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 👇 GÜNCELLENDİ: Artık sadece yolu değil, tam tıklanabilir URL dönüyor
            // Örn: http://localhost:9000/event-bucket/events/resim.jpg
            return minioUrl + "/" + bucketName + "/" + objectName;

        } catch (Exception e) {
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    // Dosya Silme (Etkinlik silindiğinde resmi de silmek için)
    public void deleteFile(String fullUrlOrObjectName) {
        try {
            // Eğer tam URL geldiyse, içinden sadece objectName'i ayıklamamız gerekebilir.
            // Şimdilik basitçe objectName geldiğini varsayıyoruz veya
            // URL gelirse parse etme mantığı ekleyebilirsin.
            // Basit kullanım için objectName bekliyoruz.

            // Eğer URL http ile başlıyorsa parse et (Opsiyonel iyileştirme)
            String objectName = fullUrlOrObjectName;
            if (fullUrlOrObjectName.startsWith("http")) {
                // http://localhost:9000/bucket-name/klasor/dosya.jpg -> klasor/dosya.jpg
                String suffix = "/" + bucketName + "/";
                int index = fullUrlOrObjectName.indexOf(suffix);
                if (index != -1) {
                    objectName = fullUrlOrObjectName.substring(index + suffix.length());
                }
            }

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            System.err.println("Error deleting file from MinIO: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}