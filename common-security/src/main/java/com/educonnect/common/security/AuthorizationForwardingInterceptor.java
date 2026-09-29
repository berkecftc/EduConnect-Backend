package com.educonnect.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AuthorizationForwardingInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(IdentityHeaders.USER_ID);
        template.removeHeader(IdentityHeaders.USER_EMAIL);
        template.removeHeader(IdentityHeaders.USER_ROLES);

        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            return;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }
}
