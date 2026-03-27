package com.educonnect.userservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

public class UpdateUserProfileRequest {

    @JsonAlias("first_name")
    private String firstName;
    @JsonAlias("last_name")
    private String lastName;
    private String bio;
    private String department;
    private String title;
    @JsonAlias("office_number")
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

