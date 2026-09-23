package com.educonnect.authservices.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "educonnect.security")
public record ServiceClientsProperties(Map<String, String> serviceClients) {

    public ServiceClientsProperties {
        serviceClients = serviceClients == null ? Map.of() : Map.copyOf(serviceClients);
    }
}
