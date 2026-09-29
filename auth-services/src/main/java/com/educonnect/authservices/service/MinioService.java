package com.educonnect.authservices.service;

import com.educonnect.common.storage.ObjectStorage;
import com.educonnect.common.storage.ObjectStorage.BucketAccess;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);
    private static final Duration PRESIGNED_URL_EXPIRY = Duration.ofMinutes(15);

    private final ObjectStorage storage;

    public MinioService(@Value("${minio.url}") String url,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name}") String bucketName,
                        StorageUrls storageUrls,
                        UploadValidator uploadValidator) {
        this.storage = ObjectStorage.connect(url, accessKey, secretKey, bucketName, BucketAccess.PRIVATE,
                storageUrls, uploadValidator);
    }

    public String createPresignedUrl(String storedUrl) {
        try {
            return storage.presignedGet(storedUrl, PRESIGNED_URL_EXPIRY);
        } catch (RuntimeException e) {
            log.error("Could not create presigned URL in bucket {}", storage.bucket(), e);
            return null;
        }
    }

    public String uploadIdCardImage(MultipartFile file, UUID userId) {
        return uploadDocument(file, "id-cards/", userId);
    }

    public void deleteIdCardImage(String imageUrl) {
        storage.delete(imageUrl);
    }

    public String uploadStudentDocument(MultipartFile file, UUID userId) {
        return uploadDocument(file, "student-documents/", userId);
    }

    public void deleteStudentDocument(String documentUrl) {
        storage.delete(documentUrl);
    }

    private String uploadDocument(MultipartFile file, String folder, UUID userId) {
        ValidatedUpload upload = storage.validate(file, UploadKind.DOCUMENT);
        return storage.put(file, upload, folder + userId + upload.extensionOr(".jpg"));
    }
}
