package com.educonnect.common.security;

import org.springframework.context.annotation.Bean;

public class ServiceTokenFeignConfiguration {

    @Bean
    public ServiceTokenRequestInterceptor serviceTokenRequestInterceptor(ServiceTokenProvider tokenProvider) {
        return new ServiceTokenRequestInterceptor(tokenProvider);
    }
}
