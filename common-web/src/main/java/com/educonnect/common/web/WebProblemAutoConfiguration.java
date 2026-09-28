package com.educonnect.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebProblemAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProblemExceptionHandler problemExceptionHandler() {
        return new ProblemExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.DataIntegrityViolationException")
    static class DataAccessConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DataAccessProblemHandler dataAccessProblemHandler() {
            return new DataAccessProblemHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.springframework.security.access.AccessDeniedException",
            "org.springframework.security.config.annotation.web.builders.HttpSecurity"})
    static class SecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SecurityProblemHandler securityProblemHandler() {
            return new SecurityProblemHandler();
        }

        @Bean
        @ConditionalOnMissingBean
        ProblemSecurityHandlers problemSecurityHandlers(ObjectMapper objectMapper) {
            return new ProblemSecurityHandlers(objectMapper);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.FeignException")
    static class FeignConfiguration {

        @Bean
        @ConditionalOnMissingBean
        FeignProblemHandler feignProblemHandler() {
            return new FeignProblemHandler();
        }
    }
}
