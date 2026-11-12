package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Yorum oluşturma isteği.
 * parentCommentId null ise üst seviye yorum, dolu ise bir üst yoruma verilen yanıttır.
 * Yanıta yanıt verilmez — sadece tek seviye derinlik desteklenir.
 */
public record CreateCommentRequest(
        @NotBlank(message = "Yorum içeriği boş olamaz")
        @Size(max = 2000, message = "Yorum en fazla 2000 karakter olabilir")
        String content,

        UUID parentCommentId
) {}

