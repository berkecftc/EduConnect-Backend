package com.educonnect.clubservice.dto.response;

import java.util.UUID;

public record ClubCatalogEntry(UUID id, String name, String about) {
}
