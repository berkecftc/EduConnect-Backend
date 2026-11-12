package com.educonnect.clubservice.dto.request;

/**
 * Öğrenci üyelik başvurusu yaparken kullanılacak DTO.
 */
public class CreateMembershipRequestDTO {

    private String message; // Opsiyonel başvuru mesajı

    public CreateMembershipRequestDTO() {}

    public CreateMembershipRequestDTO(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

