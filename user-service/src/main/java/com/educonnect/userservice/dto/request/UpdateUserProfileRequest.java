package com.educonnect.userservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public class UpdateUserProfileRequest {

    @JsonAlias("first_name")
    @Size(max = 255, message = "Ad en fazla 255 karakter olabilir")
    private String firstName;
    @JsonAlias("last_name")
    @Size(max = 255, message = "Soyad en fazla 255 karakter olabilir")
    private String lastName;
    @Size(max = 255, message = "Biyografi en fazla 255 karakter olabilir")
    private String bio;
    @Size(max = 255, message = "Bölüm en fazla 255 karakter olabilir")
    private String department;
    @Size(max = 255, message = "Unvan en fazla 255 karakter olabilir")
    private String title;
    @JsonAlias("office_number")
    @Size(max = 255, message = "Ofis numarası en fazla 255 karakter olabilir")
    private String officeNumber;

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

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOfficeNumber() {
        return officeNumber;
    }

    public void setOfficeNumber(String officeNumber) {
        this.officeNumber = officeNumber;
    }
}

