package com.educonnect.postservice.model;

public enum PostCategory {
    SORU,
    GENEL,
    DERS_NOTU,
    DUYURU;

    public boolean official() {
        return this == DUYURU;
    }
}
