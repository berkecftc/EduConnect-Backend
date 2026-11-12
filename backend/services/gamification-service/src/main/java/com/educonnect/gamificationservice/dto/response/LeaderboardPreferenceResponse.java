package com.educonnect.gamificationservice.dto.response;

import com.educonnect.gamificationservice.model.DisplayMode;

public record LeaderboardPreferenceResponse(boolean visible, DisplayMode displayMode) {
}
