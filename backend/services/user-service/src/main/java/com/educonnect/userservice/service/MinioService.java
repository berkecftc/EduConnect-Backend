package com.educonnect.userservice.service;

import com.educonnect.common.storage.ObjectStorage;
import com.educonnect.common.storage.ObjectStorage.BucketAccess;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

@Service
public class MinioService {

    private static final Duration PRESIGNED_URL_EXPIRY = Duration.ofDays(7);

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

    public String uploadFile(MultipartFile file, UUID userId) {
        ValidatedUpload upload = storage.validate(file, UploadKind.IMAGE);
        return storage.put(file, upload, "profiles/" + userId + upload.extensionOr(".jpg"));
    }

    public String getFileUrl(String objectName) {
        return storage.presignedGet(objectName, PRESIGNED_URL_EXPIRY);
    }

    public void deleteFilesAfterCommit(Collection<String> fileUrls) {
        storage.deleteAfterCommit(fileUrls);
    }
}
