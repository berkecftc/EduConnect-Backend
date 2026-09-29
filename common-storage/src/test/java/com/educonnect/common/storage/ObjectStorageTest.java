package com.educonnect.common.storage;

import com.educonnect.common.storage.ObjectStorage.BucketAccess;
import com.educonnect.common.test.MinioTestContainer;
import io.minio.GetBucketPolicyArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.MinIOContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    private static MinIOContainer minio;
    private static MinioClient admin;
    private static StorageUrls storageUrls;
    private static UploadValidator validator;

    @BeforeAll
    static void startMinio() {
        minio = new MinIOContainer(MinioTestContainer.IMAGE).withUserName("integration").withPassword("integration-secret");
        minio.start();
        admin = MinioClient.builder().endpoint(minio.getS3URL()).credentials("integration", "integration-secret").build();
        storageUrls = new StorageUrls(minio.getS3URL(), List.of());
        validator = new UploadValidator(new StorageProperties.Validation(true, DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), DataSize.ofMegabytes(1)));
    }

    @AfterAll
    static void stopMinio() {
        minio.stop();
    }

    @Test
    void publicReadAndPrivateBucketsGetTheirPolicy() throws Exception {
        ObjectStorage publicStorage = connect("public-" + suffix(), BucketAccess.PUBLIC_READ);
        ObjectStorage privateStorage = connect("private-" + suffix(), BucketAccess.PRIVATE);

        assertThat(admin.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(publicStorage.bucket()).build()))
                .contains("s3:GetObject");
        assertThat(admin.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(privateStorage.bucket()).build()))
                .isEmpty();
    }

    @Test
    void uploadedObjectsCanBeFoundByUrlAndDeleted() throws Exception {
        ObjectStorage storage = connect("files-" + suffix(), BucketAccess.PRIVATE);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG);

        String url = storage.put(file, storage.validate(file, UploadKind.IMAGE), "logos/" + UUID.randomUUID() + ".png");

        assertThat(url).startsWith(minio.getS3URL() + "/" + storage.bucket() + "/logos/");
        try (InputStream stored = storage.find(url).orElseThrow()) {
            assertThat(stored.readAllBytes()).isEqualTo(PNG);
        }
        assertThat(storage.presignedGet(url, Duration.ofMinutes(5))).contains("X-Amz-Signature");

        storage.delete(url);

        assertThat(storage.find(url)).isEmpty();
        assertThat(storage.find(storage.bucket() + "/missing.png")).isEmpty();
    }

    @Test
    void deletionAfterCommitWaitsForTheCommitAndCoversOtherBuckets() throws Exception {
        ObjectStorage own = connect("own-" + suffix(), BucketAccess.PRIVATE);
        ObjectStorage other = connect("other-" + suffix(), BucketAccess.PRIVATE);
        MockMultipartFile file = new MockMultipartFile("file", "belge.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII));
        String ownUrl = own.put(file, own.validate(file, UploadKind.DOCUMENT), "a_belge.pdf");
        String otherUrl = other.put(file, other.validate(file, UploadKind.DOCUMENT), "b_belge.pdf");

        TransactionSynchronizationManager.initSynchronization();
        try {
            own.deleteAfterCommit(List.of(ownUrl, otherUrl, "", ownUrl));
            assertThat(exists(own.bucket(), "a_belge.pdf")).isTrue();

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(exists(own.bucket(), "a_belge.pdf")).isFalse();
        assertThat(exists(other.bucket(), "b_belge.pdf")).isFalse();
        assertThat(own.originalFileName(ownUrl)).isEqualTo("belge.pdf");
    }

    private static ObjectStorage connect(String bucket, BucketAccess access) {
        return ObjectStorage.connect(minio.getS3URL(), "integration", "integration-secret", bucket, access,
                storageUrls, validator);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static boolean exists(String bucket, String objectName) {
        try {
            admin.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
