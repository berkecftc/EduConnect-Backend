package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AnnouncementRequest {
    @NotBlank(message = "Duyuru başlığı boş olamaz")
    @Size(max = 255, message = "Duyuru başlığı en fazla 255 karakter olabilir")
    private String title;

    @NotBlank(message = "Duyuru içeriği boş olamaz")
    private String content;

    public AnnouncementRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}

