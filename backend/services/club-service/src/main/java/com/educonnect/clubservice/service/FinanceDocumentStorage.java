package com.educonnect.clubservice.service;

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
public class FinanceDocumentStorage {

    private final ObjectStorage storage;

    public FinanceDocumentStorage(@Value("${minio.url}") String url,
                                  @Value("${minio.access-key}") String accessKey,
                                  @Value("${minio.secret-key}") String secretKey,
                                  @Value("${educonnect.club.finance.bucket:club-finance}") String bucketName,
                                  StorageUrls storageUrls,
                                  UploadValidator uploadValidator) {
        this.storage = ObjectStorage.connect(url, accessKey, secretKey, bucketName, BucketAccess.PRIVATE,
                storageUrls, uploadValidator);
    }

    public StoredDocument store(MultipartFile file, UUID clubId) {
        ValidatedUpload upload = storage.validate(file, UploadKind.DOCUMENT);
        String objectName = clubId + "/" + UUID.randomUUID() + upload.extensionOr(".pdf");
        storage.put(file, upload, objectName);
        return new StoredDocument(objectName, upload.safeOriginalName());
    }

    public InputStream open(String objectName) {
        return storage.find(objectName)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Belge bulunamadı."));
    }

    public record StoredDocument(String objectName, String fileName) {
    }
}
