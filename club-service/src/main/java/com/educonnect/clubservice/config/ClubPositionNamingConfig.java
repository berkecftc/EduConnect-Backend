package com.educonnect.clubservice.config;

import com.educonnect.clubservice.model.ClubPosition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClubPositionNamingConfig {

    public ClubPositionNamingConfig(@Value("${educonnect.club.legacy-position-names:true}") boolean legacyNames) {
        ClubPosition.useLegacyApiNames(legacyNames);
    }
}
