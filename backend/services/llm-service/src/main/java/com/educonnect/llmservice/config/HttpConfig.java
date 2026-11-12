package com.educonnect.llmservice.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class HttpConfig {

    /**
     * Spring Boot'un varsayılan HTTP İstemcisini (RestClient) eziyoruz.
     * LLM'in uzun yanıtları için okuma sabrını artırıyoruz.
     */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return builder -> builder.requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(
                        HttpClientSettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(10))
                                .withReadTimeout(Duration.ofMinutes(10))
                )
        );
    }
}