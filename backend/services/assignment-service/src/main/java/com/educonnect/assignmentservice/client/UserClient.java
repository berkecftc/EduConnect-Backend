package com.educonnect.assignmentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.educonnect.assignmentservice.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/users", configuration = FeignClientConfig.class)
public interface UserClient {
    @GetMapping("/profile/{userId}")
    UserProfileDTO getUserProfile(@PathVariable("userId") UUID userId);

    // İç DTO sınıfı
    @JsonIgnoreProperties(ignoreUnknown = true)
    class UserProfileDTO {
        private UUID id;
        private String firstName;
        private String lastName;
        private String studentNumber;
        private String role;

        @Override
        public String toString() {
            return "UserProfileDTO{" +
                    "id=" + id +
                    ", firstName='" + firstName + '\'' +
                    ", lastName='" + lastName + '\'' +
                    ", studentNumber='" + studentNumber + '\'' +
                    ", role='" + role + '\'' +
                    '}';
        }

        public UserProfileDTO() {}

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getStudentNumber() { return studentNumber; }
        public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}




