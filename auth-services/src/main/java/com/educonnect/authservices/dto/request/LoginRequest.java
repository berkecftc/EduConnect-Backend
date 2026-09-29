package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    @NotBlank(message = "E-posta boş olamaz")
    @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
    private String email;
    @NotEmpty(message = "Şifre boş olamaz")
    private String password;


    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}