package com.educonnect.authservices.service;

import com.educonnect.authservices.config.ServiceClientsProperties;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ServiceClientAuthenticator {

    private final ServiceClientsProperties properties;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final String unknownClientHash = passwordEncoder.encode(UUID.randomUUID().toString());

    public ServiceClientAuthenticator(ServiceClientsProperties properties) {
        this.properties = properties;
    }

    public boolean authenticate(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {
            return false;
        }
        String encodedSecret = properties.serviceClients().get(clientId);
        if (encodedSecret == null) {
            passwordEncoder.matches(clientSecret, unknownClientHash);
            return false;
        }
        return passwordEncoder.matches(clientSecret, encodedSecret);
    }
}
