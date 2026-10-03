package com.educonnect.gamificationservice.dto.request;

import com.educonnect.gamificationservice.model.DisplayMode;
import jakarta.validation.constraints.NotNull;

public record LeaderboardPreferenceRequest(
        @NotNull(message = "Tabloda görünme tercihi zorunludur")
        Boolean visible,

        @NotNull(message = "Görünen ad biçimi zorunludur")
        DisplayMode displayMode
) {
}
