package com.educonnect.authservices.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StaffGrantsRequest(@NotEmpty(message = "En az bir yetki verilmelidir") List<StaffAccountRequest.@Valid Grant> grants) {
}
