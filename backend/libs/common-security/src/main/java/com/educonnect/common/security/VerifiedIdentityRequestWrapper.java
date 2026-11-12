package com.educonnect.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

class VerifiedIdentityRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> identity;

    VerifiedIdentityRequestWrapper(HttpServletRequest request, Map<String, String> identity) {
        super(request);
        this.identity = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        identity.forEach((name, value) -> {
            if (value != null) {
                this.identity.put(name, value);
            }
        });
    }

    @Override
    public String getHeader(String name) {
        if (IdentityHeaders.isIdentityHeader(name)) {
            return identity.get(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (IdentityHeaders.isIdentityHeader(name)) {
            String value = identity.get(name);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            String name = original.nextElement();
            if (!IdentityHeaders.isIdentityHeader(name)) {
                names.add(name);
            }
        }
        names.addAll(identity.keySet());
        return Collections.enumeration(names);
    }
}
