package com.educonnect.courseservice.service;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {
    private final MinioClient minioClient;
    @Value("${minio.bucket-name}") private String bucketName;

    public MinioService(MinioClient minioClient) { this.minioClient = minioClient; }

    public String uploadFile(MultipartFile file) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // Localhost URL'i
            return "http://localhost:9000/" + bucketName + "/" + fileName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO Yükleme Hatası: " + e.getMessage());
        }
    }

    /**
     * MinIO'dan dosya indirir.
     * @param fileUrl Dosyanın tam URL'si (örn: http://localhost:9000/bucket/dosya.pdf)
     * @return InputStream olarak dosya verisi
     */
    public InputStream downloadFile(String fileUrl) {
        try {
            // URL'den objectName'i çıkar: http://localhost:9000/bucket/dosya.pdf -> dosya.pdf
            String objectName = extractObjectName(fileUrl);

            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO İndirme Hatası: " + e.getMessage());
        }
    }

    /**
     * URL'den dosya adını (object name) çıkarır.
     */
    public String extractObjectName(String fileUrl) {
        // http://localhost:9000/bucketName/fileName şeklinde URL'den fileName'i çıkar
        String prefix = "http://localhost:9000/" + bucketName + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        // Eğer zaten sadece dosya adıysa direkt döndür
        return fileUrl;
    }

    /**
     * URL'den orijinal dosya adını çıkarır (UUID prefix'i kaldırarak).
     */
    public String extractOriginalFileName(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        // UUID_originalname.ext formatından orijinal adı çıkar
        int underscoreIndex = objectName.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < objectName.length() - 1) {
            return objectName.substring(underscoreIndex + 1);
        }
        return objectName;
    }
}