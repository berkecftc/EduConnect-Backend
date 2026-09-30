package com.educonnect.clubservice.dto.response;

public final class AcademicYears {

    private AcademicYears() {
    }

    public static String label(int academicYear) {
        return (academicYear - 1) + "-" + academicYear;
    }
}
