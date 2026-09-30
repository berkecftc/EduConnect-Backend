package com.educonnect.clubservice.dto.response;

import com.educonnect.common.web.BadRequestException;

public final class AcademicYears {

    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2200;

    private AcademicYears() {
    }

    public static int requireSupported(int academicYear) {
        if (academicYear < MIN_YEAR || academicYear > MAX_YEAR) {
            throw new BadRequestException("INVALID_ACADEMIC_YEAR",
                    "Akademik yıl " + MIN_YEAR + " ile " + MAX_YEAR + " arasında olmalı.");
        }
        return academicYear;
    }

    public static String label(int academicYear) {
        if (academicYear < MIN_YEAR || academicYear > MAX_YEAR) {
            throw new BadRequestException("INVALID_ACADEMIC_YEAR",
                    "Akademik yıl " + MIN_YEAR + " ile " + MAX_YEAR + " arasında olmalı.");
        }
        return (academicYear - 1) + "-" + academicYear;
    }
}
