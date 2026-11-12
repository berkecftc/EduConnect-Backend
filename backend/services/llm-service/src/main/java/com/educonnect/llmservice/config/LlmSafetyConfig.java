package com.educonnect.llmservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmSafetyProperties.class)
public class LlmSafetyConfig {
}
