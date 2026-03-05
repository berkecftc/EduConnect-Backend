package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank(message = "Başlık boş olamaz")
        @Size(max = 255, message = "Başlık en fazla 255 karakter olabilir")
        String title,

        @NotBlank(message = "İçerik boş olamaz")
        String content,

        @NotNull(message = "Kategori boş olamaz")
        PostCategory category
) {}

