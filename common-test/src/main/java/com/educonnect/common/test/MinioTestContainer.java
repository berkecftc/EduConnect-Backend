package com.educonnect.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MinioTestContainer {

    public static final DockerImageName IMAGE = DockerImageName.parse(
                    "quay.io/minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e")
            .asCompatibleSubstituteFor("minio/minio");

    @Bean
    public MinIOContainer minioContainer() {
        return new MinIOContainer(IMAGE)
                .withUserName("integration")
                .withPassword("integration-secret");
    }

    @Bean
    public DynamicPropertyRegistrar minioProperties(MinIOContainer minio) {
        return registry -> {
            registry.add("minio.url", minio::getS3URL);
            registry.add("minio.access-key", minio::getUserName);
            registry.add("minio.secret-key", minio::getPassword);
        };
    }
}
