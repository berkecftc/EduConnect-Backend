package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank(message = "Yeni e-posta boş olamaz") @Email(message = "Geçerli bir e-posta adresi girin")
        @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir") String newEmail,
        @NotBlank(message = "Mevcut şifre zorunludur") String currentPassword) {
}
