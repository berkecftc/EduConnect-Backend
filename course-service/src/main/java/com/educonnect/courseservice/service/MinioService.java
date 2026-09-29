package com.educonnect.courseservice.service;

import com.educonnect.common.storage.ObjectStorage;
import com.educonnect.common.storage.ObjectStorage.BucketAccess;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import com.educonnect.common.web.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {

    private final ObjectStorage storage;

    public MinioService(@Value("${minio.url}") String url,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket-name}") String bucketName,
                        StorageUrls storageUrls,
                        UploadValidator uploadValidator) {
        this.storage = ObjectStorage.connect(url, accessKey, secretKey, bucketName, BucketAccess.UNMANAGED,
                storageUrls, uploadValidator);
    }

    public String uploadFile(MultipartFile file) {
        ValidatedUpload upload = storage.validate(file, UploadKind.IMAGE);
        return storage.put(file, upload, UUID.randomUUID() + "_" + upload.safeOriginalName());
    }

    public InputStream downloadFile(String fileUrl) {
        return storage.find(fileUrl)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "Dosya bulunamadı."));
    }

    public String extractObjectName(String fileUrl) {
        return storage.objectName(fileUrl);
    }

    public String canonicalUrl(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        return storage.url(objectName);
    }

    public String extractOriginalFileName(String fileUrl) {
        return storage.originalFileName(fileUrl);
    }
}
