package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.response.UserSummaryDTO;
import com.educonnect.userservice.Repository.AcademicianRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users/search")
public class UserSearchController {

    private final AcademicianRepository academicianRepository;

    // Açık kurucu ile bağımlılığı enjekte et (Lombok'a gerek kalmadan)
    public UserSearchController(AcademicianRepository academicianRepository) {
        this.academicianRepository = academicianRepository;
    }

    // Örnek Çağrı: GET /api/users/search/academicians?query=Ahmet
    @GetMapping("/academicians")
    public ResponseEntity<List<UserSummaryDTO>> searchAcademicians(@RequestParam String query) {

        return ResponseEntity.ok(
                academicianRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query)
                        .stream()
                        .map(acc -> new UserSummaryDTO(
                                acc.getId(),
                                acc.getFirstName(),
                                acc.getLastName(),
                                acc.getTitle()
                        ))
                        .limit(10) // Performans için en fazla 10 sonuç dön
                        .collect(Collectors.toList())
        );
    }
}