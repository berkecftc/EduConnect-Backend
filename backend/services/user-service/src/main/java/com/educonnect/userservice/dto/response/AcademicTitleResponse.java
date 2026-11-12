package com.educonnect.userservice.dto.response;

import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.StaffCategory;

public record AcademicTitleResponse(AcademicTitle code, String label, StaffCategory category) {

    public static AcademicTitleResponse of(AcademicTitle title) {
        return new AcademicTitleResponse(title, title.label(), title.category());
    }
}
