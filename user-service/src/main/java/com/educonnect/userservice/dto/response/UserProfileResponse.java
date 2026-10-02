package com.educonnect.userservice.dto.response;
import java.io.Serializable;
import java.util.UUID;


public class UserProfileResponse implements Serializable{
    private UUID programId;
    private String programName;
    private String programLevel;
    private String facultyName;
    private UUID departmentId;
    private Integer entryYear;
    private Integer classYear;

    private static final long serialVersionUID = -8383490846158348982L;

    private UUID id;
    private String firstName;
    private String lastName;
    private String email; // Kullanıcının e-posta adresi
    private String profileImageUrl;
    private String bio;
    private String role; // "Student" veya "Academician"

    private String studentNumber; // Sadece öğrenciyse dolu olacak
    private String title; // Sadece akademisyense dolu olacak
    private String department; // Ortak olabilir

    public UserProfileResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public UserProfileResponse withoutEmail() {
        UserProfileResponse copy = new UserProfileResponse();
        copy.setId(id);
        copy.setFirstName(firstName);
        copy.setLastName(lastName);
        copy.setProfileImageUrl(profileImageUrl);
        copy.setBio(bio);
        copy.setRole(role);
        copy.setStudentNumber(studentNumber);
        copy.setTitle(title);
        copy.setDepartment(department);
        copy.setProgramId(programId);
        copy.setProgramName(programName);
        copy.setProgramLevel(programLevel);
        copy.setFacultyName(facultyName);
        copy.setDepartmentId(departmentId);
        copy.setEntryYear(entryYear);
        copy.setClassYear(classYear);
        return copy;
    }
    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getProgramLevel() { return programLevel; }
    public void setProgramLevel(String programLevel) { this.programLevel = programLevel; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }

    public Integer getEntryYear() { return entryYear; }
    public void setEntryYear(Integer entryYear) { this.entryYear = entryYear; }

    public Integer getClassYear() { return classYear; }
    public void setClassYear(Integer classYear) { this.classYear = classYear; }
}
