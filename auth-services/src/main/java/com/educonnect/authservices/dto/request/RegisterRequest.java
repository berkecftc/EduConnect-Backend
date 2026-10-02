package com.educonnect.authservices.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class RegisterRequest {
    @JsonAlias({"e-mail", "email"})
    @NotBlank(message = "E-posta boş olamaz")
    @Email(message = "Geçerli bir e-posta adresi girin")
    @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
    private String email;
    @NotEmpty(message = "Şifre boş olamaz")
    private String password;
    @JsonProperty("first_name")
    @JsonAlias({"first_name", "firstName"})
    @NotBlank(message = "Ad boş olamaz")
    @Size(max = 255, message = "Ad en fazla 255 karakter olabilir")
    private String firstName;
    @JsonProperty("last_name")
    @JsonAlias({"last_name", "lastName"})
    @NotBlank(message = "Soyad boş olamaz")
    @Size(max = 255, message = "Soyad en fazla 255 karakter olabilir")
    private String lastName;
    @JsonProperty("student_id")
    @JsonAlias({"student_id", "studentNumber", "studentId", "student_number"})
    @Size(max = 255, message = "Öğrenci numarası en fazla 255 karakter olabilir")
    private String studentId;
    @Size(max = 255, message = "Bölüm en fazla 255 karakter olabilir")
    private String department;

    @Size(max = 255, message = "Unvan en fazla 255 karakter olabilir")
    private String title; // Örn: "Prof. Dr."
    @Size(max = 255, message = "Ofis numarası en fazla 255 karakter olabilir")
    private String officeNumber; // Örn: "A-101"

    @JsonAlias({"program_id", "programId"})
    private UUID programId;

    @JsonAlias({"entry_year", "entryYear"})
    @Min(value = 1950, message = "Giriş yılı geçersiz")
    @Max(value = 2200, message = "Giriş yılı geçersiz")
    private Integer entryYear;

    @JsonAlias({"department_id", "departmentId"})
    private UUID departmentId;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOfficeNumber() { return officeNumber; }
    public void setOfficeNumber(String officeNumber) { this.officeNumber = officeNumber; }


    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }
    public Integer getEntryYear() { return entryYear; }
    public void setEntryYear(Integer entryYear) { this.entryYear = entryYear; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
}
