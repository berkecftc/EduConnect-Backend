package com.educonnect.clubservice.service;

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

import java.time.Duration;
import java.util.List;
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
        return uploadFile(file, "profiles", userId.toString());
    }

    public String uploadFile(MultipartFile file, String folder, String nameBase) {
        ValidatedUpload upload = storage.validate(file, UploadKind.IMAGE);
        String objectName = SafeFileNames.pathSegment(folder, "misc") + "/"
                + SafeFileNames.pathSegment(nameBase, UUID.randomUUID().toString()) + upload.extensionOr(".jpg");
        return storage.put(file, upload, objectName);
    }

    public void deleteAfterCommit(String objectName) {
        if (objectName != null) {
            storage.deleteAfterCommit(List.of(objectName));
        }
    }

    public String getFileUrl(String objectName) {
        return storage.presignedGet(objectName, PRESIGNED_URL_EXPIRY);
    }
}
