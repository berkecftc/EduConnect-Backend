package com.educonnect.eventservice.dto.request;

import java.util.UUID;

public record VerifyQrRequest(String qrCode, UUID eventId) {}
