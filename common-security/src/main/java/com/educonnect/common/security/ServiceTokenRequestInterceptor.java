package com.educonnect.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;

public class ServiceTokenRequestInterceptor implements RequestInterceptor {

    private final ServiceTokenProvider tokenProvider;

    public ServiceTokenRequestInterceptor(ServiceTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        IdentityHeaders.ALL.forEach(template::removeHeader);
        template.removeHeader(HttpHeaders.AUTHORIZATION);
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken());
    }
}
