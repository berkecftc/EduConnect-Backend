package com.educonnect.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MinioTestContainer {

    public static final DockerImageName IMAGE = DockerImageName.parse(
                    "pgsty/minio@sha256:b6bfe7239bfc83fb90d31612d9704d86039dd714f7904b3f1ad68f211e602372")
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
