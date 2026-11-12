package com.educonnect.assignmentservice.service;

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
import java.util.Collection;
import java.util.UUID;

@Service
public class MinioService {

    private final ObjectStorage storage;
    private final StorageUrls storageUrls;

    public MinioService(@Value("${minio.url}") String minioUrl,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name:${minio.bucket-name}}") String bucketName,
                        StorageUrls storageUrls,
                        UploadValidator uploadValidator) {
        this.storage = ObjectStorage.connect(minioUrl, accessKey, secretKey, bucketName, BucketAccess.PRIVATE,
                storageUrls, uploadValidator);
        this.storageUrls = storageUrls;
    }

    public String uploadFile(MultipartFile file) {
        ValidatedUpload upload = storage.validate(file, UploadKind.ATTACHMENT);
        return storage.put(file, upload, UUID.randomUUID() + "_" + upload.safeOriginalName());
    }

    public InputStream downloadFile(String fileUrl) {
        return storage.find(fileUrl)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "Dosya bulunamadı."));
    }

    public String extractObjectName(String fileUrl) {
        return storage.objectName(fileUrl);
    }

    public String normalizeToFullUrl(String fileUrlOrObjectName) {
        if (fileUrlOrObjectName == null || fileUrlOrObjectName.isBlank()) {
            return fileUrlOrObjectName;
        }
        String path = storageUrls.toStoredValue(fileUrlOrObjectName);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return storage.url(storage.objectName(path));
    }

    public String extractOriginalFileName(String fileUrl) {
        return storage.originalFileName(fileUrl);
    }

    public void deleteFilesAfterCommit(Collection<String> fileUrls) {
        storage.deleteAfterCommit(fileUrls);
    }
}
