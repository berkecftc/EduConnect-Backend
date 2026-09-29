package com.educonnect.eventservice.service;

import com.educonnect.common.storage.ObjectStorage;
import com.educonnect.common.storage.ObjectStorage.BucketAccess;
import com.educonnect.common.storage.SafeFileNames;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class MinioService {

    private final ObjectStorage storage;

    public MinioService(@Value("${minio.url}") String url,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket.name}") String bucketName,
                        StorageUrls storageUrls,
                        UploadValidator uploadValidator) {
        this.storage = ObjectStorage.connect(url, accessKey, secretKey, bucketName, BucketAccess.PUBLIC_READ,
                storageUrls, uploadValidator);
    }

    public ValidatedUpload validateImage(MultipartFile file) {
        return storage.validate(file, UploadKind.IMAGE);
    }

    public String uploadFile(MultipartFile file, String folder, String fileName) {
        ValidatedUpload upload = validateImage(file);
        String objectName = SafeFileNames.pathSegment(folder, "images") + "/"
                + SafeFileNames.pathSegment(fileName, UUID.randomUUID().toString()) + upload.extensionOr(".jpg");
        return storage.put(file, upload, objectName);
    }

    public void deleteFile(String fullUrlOrObjectName) {
        storage.delete(fullUrlOrObjectName);
    }
}
