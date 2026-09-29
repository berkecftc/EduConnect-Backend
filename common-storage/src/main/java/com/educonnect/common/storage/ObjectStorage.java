package com.educonnect.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ObjectStorage {

    public enum BucketAccess {
        PUBLIC_READ,
        PRIVATE,
        UNMANAGED
    }

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {"AWS": ["*"]},
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/*"]
                }
              ]
            }""";

    private final MinioClient client;
    private final PresignedUrls presignedUrls;
    private final String bucket;
    private final StorageUrls storageUrls;
    private final UploadValidator uploadValidator;

    ObjectStorage(MinioClient client, PresignedUrls presignedUrls, String bucket,
                  StorageUrls storageUrls, UploadValidator uploadValidator) {
        this.client = client;
        this.presignedUrls = presignedUrls;
        this.bucket = bucket;
        this.storageUrls = storageUrls;
        this.uploadValidator = uploadValidator;
    }

    public static ObjectStorage connect(String endpoint, String accessKey, String secretKey, String bucket,
                                        BucketAccess access, StorageUrls storageUrls, UploadValidator uploadValidator) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ObjectStorage storage = new ObjectStorage(client, new PresignedUrls(storageUrls, accessKey, secretKey),
                bucket, storageUrls, uploadValidator);
        storage.prepare(access);
        return storage;
    }

    void prepare(BucketAccess access) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            }
            switch (access) {
                case PUBLIC_READ -> client.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(PUBLIC_READ_POLICY.formatted(bucket))
                        .build());
                case PRIVATE -> client.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
                case UNMANAGED -> {
                }
            }
            log.info("MinIO bucket '{}' ready ({})", bucket, access);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO bucket could not be prepared: " + bucket, e);
        }
    }

    public String bucket() {
        return bucket;
    }

    public ValidatedUpload validate(MultipartFile file, UploadKind kind) {
        return uploadValidator.validate(file, kind);
    }

    public String put(MultipartFile file, ValidatedUpload upload, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), null)
                    .contentType(upload.contentType())
                    .build());
            return storageUrls.url(bucket, objectName);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO upload failed: " + bucket + "/" + objectName, e);
        }
    }

    public Optional<InputStream> find(String urlOrObjectName) {
        try {
            return Optional.of(client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName(urlOrObjectName))
                    .build()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IllegalStateException("MinIO download failed: " + bucket, e);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("MinIO download failed: " + bucket, e);
        }
    }

    public String objectName(String urlOrObjectName) {
        return storageUrls.objectName(urlOrObjectName, bucket);
    }

    public String url(String objectName) {
        return storageUrls.url(bucket, objectName);
    }

    public String originalFileName(String urlOrObjectName) {
        String objectName = objectName(urlOrObjectName);
        int underscoreIndex = objectName.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < objectName.length() - 1) {
            return objectName.substring(underscoreIndex + 1);
        }
        return objectName;
    }

    public String presignedGet(String urlOrObjectName, Duration expiry) {
        if (urlOrObjectName == null || urlOrObjectName.isBlank()) {
            return null;
        }
        return presignedUrls.get(bucket, objectName(urlOrObjectName), expiry);
    }

    public void delete(String urlOrObjectName) {
        if (urlOrObjectName == null || urlOrObjectName.isBlank()) {
            return;
        }
        remove(bucket, objectName(urlOrObjectName));
    }

    public void deleteAfterCommit(Collection<String> fileUrls) {
        List<String> urls = fileUrls.stream().filter(url -> url != null && !url.isBlank()).distinct().toList();
        if (urls.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    urls.forEach(ObjectStorage.this::deleteLocated);
                }
            });
        } else {
            urls.forEach(this::deleteLocated);
        }
    }

    private void deleteLocated(String fileUrl) {
        storageUrls.locate(fileUrl).ifPresent(object -> remove(object.bucket(), object.objectName()));
    }

    private void remove(String targetBucket, String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(targetBucket).object(objectName).build());
        } catch (Exception e) {
            log.warn("MinIO object could not be deleted: {}/{} ({})", targetBucket, objectName, e.getMessage());
        }
    }
}
