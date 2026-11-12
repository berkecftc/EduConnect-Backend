package com.educonnect.userservice.dto.response;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserProfileResponseDTO {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String profileImageUrl;
    private String role;
    private String studentNumber;
    private String title;
    private String department;
    private String bio;

    private int profileCompletionPercentage;
    private GamificationSummaryDTO gamification;
    private List<RecentPostDTO> recentPosts = new ArrayList<>();

    public UserProfileResponseDTO() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public int getProfileCompletionPercentage() {
        return profileCompletionPercentage;
    }

    public void setProfileCompletionPercentage(int profileCompletionPercentage) {
        this.profileCompletionPercentage = profileCompletionPercentage;
    }

    public GamificationSummaryDTO getGamification() {
        return gamification;
    }

    public void setGamification(GamificationSummaryDTO gamification) {
        this.gamification = gamification;
    }

    public List<RecentPostDTO> getRecentPosts() {
        return recentPosts;
    }

    public void setRecentPosts(List<RecentPostDTO> recentPosts) {
        this.recentPosts = recentPosts == null ? List.of() : recentPosts;
    }
}

