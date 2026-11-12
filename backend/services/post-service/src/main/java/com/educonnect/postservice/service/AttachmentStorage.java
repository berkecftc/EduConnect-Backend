package com.educonnect.postservice.service;

import com.educonnect.common.storage.ObjectStorage;
import com.educonnect.common.storage.ObjectStorage.BucketAccess;
import com.educonnect.common.storage.StorageUrls;
import com.educonnect.common.storage.UploadKind;
import com.educonnect.common.storage.UploadValidator;
import com.educonnect.common.storage.ValidatedUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty("minio.url")
public class AttachmentStorage {

    private final Supplier<ObjectStorage> connector;
    private volatile ObjectStorage storage;

    public AttachmentStorage(@Value("${minio.url}") String url,
                             @Value("${minio.access-key}") String accessKey,
                             @Value("${minio.secret-key}") String secretKey,
                             @Value("${minio.bucket-name:post-attachments}") String bucketName,
                             StorageUrls storageUrls,
                             UploadValidator uploadValidator) {
        this.connector = () -> ObjectStorage.connect(url, accessKey, secretKey, bucketName, BucketAccess.PRIVATE,
                storageUrls, uploadValidator);
    }

    public String store(MultipartFile file) {
        ObjectStorage objects = storage();
        ValidatedUpload upload = objects.validate(file, UploadKind.ATTACHMENT);
        return objects.put(file, upload, UUID.randomUUID() + "_" + upload.safeOriginalName());
    }

    public Optional<InputStream> open(String url) {
        return storage().find(url);
    }

    public String fileName(String url) {
        return storage().originalFileName(url);
    }

    public void deleteAfterCommit(String url) {
        storage().deleteAfterCommit(List.of(url));
    }

    private ObjectStorage storage() {
        ObjectStorage current = storage;
        if (current == null) {
            synchronized (this) {
                current = storage;
                if (current == null) {
                    current = connector.get();
                    storage = current;
                }
            }
        }
        return current;
    }
}
