package com.educonnect.common.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.Http;

import java.time.Duration;

public final class PresignedUrls {

    static final String REGION = "us-east-1";

    private final MinioClient signingClient;

    public PresignedUrls(StorageUrls storageUrls, String accessKey, String secretKey) {
        this.signingClient = MinioClient.builder()
                .endpoint(storageUrls.publicBaseUrl())
                .region(REGION)
                .credentials(accessKey, secretKey)
                .build();
    }

    public String get(String bucket, String objectName, Duration expiry) {
        try {
            return signingClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry((int) expiry.toSeconds())
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not presign " + bucket + "/" + objectName, e);
        }
    }
}
