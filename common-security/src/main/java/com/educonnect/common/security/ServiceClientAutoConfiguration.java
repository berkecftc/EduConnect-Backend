package com.educonnect.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(ServiceClientProperties.class)
public class ServiceClientAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalancerClient")
    static class LoadBalancedTokenClientConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ServiceTokenProvider serviceTokenProvider(ServiceClientProperties properties,
                                                         Environment environment,
                                                         ObjectProvider<LoadBalancerClient> loadBalancerClient) {
            RestClient.Builder builder = baseBuilder(properties);
            LoadBalancerClient client = loadBalancerClient.getIfAvailable();
            if (client != null) {
                builder.requestInterceptor(new LoadBalancerInterceptor(client));
            }
            return createProvider(builder.build(), properties, environment);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenProvider serviceTokenProvider(ServiceClientProperties properties, Environment environment) {
        return createProvider(baseBuilder(properties).build(), properties, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenHttpRequestInterceptor serviceTokenHttpRequestInterceptor(ServiceTokenProvider tokenProvider) {
        return new ServiceTokenHttpRequestInterceptor(tokenProvider);
    }

    private static RestClient.Builder baseBuilder(ServiceClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static ServiceTokenProvider createProvider(RestClient restClient,
                                                       ServiceClientProperties properties,
                                                       Environment environment) {
        String clientId = properties.id() != null ? properties.id() : environment.getProperty("spring.application.name");
        return new ServiceTokenProvider(restClient, clientId, properties.secret(), properties.tokenUri(),
                properties.refreshSkew(), Clock.systemUTC());
    }
}
