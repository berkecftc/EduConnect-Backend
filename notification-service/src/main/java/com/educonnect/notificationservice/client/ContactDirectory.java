package com.educonnect.notificationservice.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ContactDirectory {

    static final String CONTACTS_URL = "http://AUTH-SERVICES/api/auth/internal/users/contacts";
    private static final int BATCH = 500;

    private final RestTemplate restTemplate;

    public ContactDirectory(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record Contact(UUID id, String email, boolean active) {
    }

    public Map<UUID, Contact> lookup(Collection<UUID> userIds) {
        List<UUID> ids = List.copyOf(userIds);
        Map<UUID, Contact> contacts = new HashMap<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<UUID> batch = ids.subList(from, Math.min(ids.size(), from + BATCH));
            List<Contact> found = restTemplate.exchange(CONTACTS_URL, HttpMethod.POST, new HttpEntity<>(batch),
                    new ParameterizedTypeReference<List<Contact>>() {
                    }).getBody();
            if (found != null) {
                found.forEach(contact -> contacts.put(contact.id(), contact));
            }
        }
        return contacts;
    }
}
