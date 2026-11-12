package com.educonnect.common.storage;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StorageUrls storageUrls(StorageProperties properties, Environment environment) {
        String minioUrl = environment.getProperty("minio.url");
        String publicBaseUrl = properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()
                ? properties.publicBaseUrl()
                : minioUrl;
        List<String> legacy = new ArrayList<>(properties.legacyBaseUrls());
        if (minioUrl != null) {
            legacy.add(minioUrl);
        }
        return new StorageUrls(publicBaseUrl, legacy);
    }

    @Bean
    @ConditionalOnMissingBean
    public UploadValidator uploadValidator(StorageProperties properties) {
        return new UploadValidator(properties.validation());
    }
}
