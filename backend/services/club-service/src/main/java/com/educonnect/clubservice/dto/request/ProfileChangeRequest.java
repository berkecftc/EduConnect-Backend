package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ClubCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileChangeRequest(@Size(max = 5000) String about,
                                   ClubCategory category,
                                   @Email @Size(max = 255) String contactEmail,
                                   @Size(max = 255) @Pattern(regexp = LINK) String websiteUrl,
                                   @Size(max = 255) @Pattern(regexp = LINK) String instagramUrl,
                                   @Size(max = 255) @Pattern(regexp = LINK) String xUrl,
                                   @Size(max = 255) @Pattern(regexp = LINK) String linkedinUrl,
                                   @Size(max = 1000) String note) {

    static final String LINK = "^$|^https://\\S+$";
}
