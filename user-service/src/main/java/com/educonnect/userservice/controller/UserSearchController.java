package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.response.UserSummaryDTO;
import com.educonnect.userservice.repository.AcademicianRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/users/search")
public class UserSearchController {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final int MAX_RESULTS = 10;

    private final AcademicianRepository academicianRepository;

    public UserSearchController(AcademicianRepository academicianRepository) {
        this.academicianRepository = academicianRepository;
    }

    @GetMapping("/academicians")
    public ResponseEntity<List<UserSummaryDTO>> searchAcademicians(@RequestParam String query) {
        String term = searchKey(query);
        if (term.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(academicianRepository.searchActive(term, Limit.of(MAX_RESULTS)).stream()
                .map(UserSummaryDTO::of)
                .toList());
    }

    static String searchKey(String query) {
        return query.strip().replaceAll("\\s+", " ").toUpperCase(TURKISH).replace('İ', 'I');
    }
}
