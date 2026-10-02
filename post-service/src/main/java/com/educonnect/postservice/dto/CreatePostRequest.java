package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PublisherType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePostRequest(
        @NotBlank(message = "Başlık boş olamaz")
        @Size(max = 255, message = "Başlık en fazla 255 karakter olabilir")
        String title,

        @NotBlank(message = "İçerik boş olamaz")
        String content,

        PostCategory category,

        PublisherType publisherType,

        UUID clubId,

        UUID courseId,

        @Size(max = 255, message = "Yayımlayan birim adı en fazla 255 karakter olabilir")
        String publisherName,

        Boolean commentsDisabled
) {

    public PostCategory categoryOrDefault() {
        return category != null ? category : PostCategory.SORU;
    }
}
