package com.educonnect.authservices.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StaffAccountRequest(
        @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta adresi girin")
        @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir") String email,
        @NotBlank(message = "Görünen ad zorunludur") @Size(max = 200, message = "Görünen ad en fazla 200 karakter olabilir") String displayName,
        @NotEmpty(message = "En az bir yetki verilmelidir") List<@Valid Grant> grants) {

    public record Grant(@NotNull(message = "Yetki zorunludur") String permission, List<UUID> facultyIds) {
    }
}
