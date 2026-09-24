package com.educonnect.clubservice.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClubPositionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void restoreDefault() {
        ClubPosition.useLegacyApiNames(true);
    }

    @Test
    void acceptsLegacyAndNewNames() {
        assertThat(ClubPosition.fromValue("ROLE_CLUB_OFFICIAL")).isEqualTo(ClubPosition.PRESIDENT);
        assertThat(ClubPosition.fromValue("ROLE_SECRETARY")).isEqualTo(ClubPosition.GENERAL_SECRETARY);
        assertThat(ClubPosition.fromValue("GENERAL_SECRETARY")).isEqualTo(ClubPosition.GENERAL_SECRETARY);
        assertThat(ClubPosition.fromValue("board_member")).isEqualTo(ClubPosition.BOARD_MEMBER);
        assertThat(ClubPosition.fromValue(" ")).isNull();
        assertThatThrownBy(() -> ClubPosition.fromValue("ROLE_ADMIN")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesLegacyNamesWhileFlagIsOn() throws Exception {
        ClubPosition.useLegacyApiNames(true);
        assertThat(objectMapper.writeValueAsString(ClubPosition.PRESIDENT)).isEqualTo("\"ROLE_CLUB_OFFICIAL\"");
        assertThat(objectMapper.writeValueAsString(ClubPosition.GENERAL_SECRETARY)).isEqualTo("\"ROLE_SECRETARY\"");
    }

    @Test
    void serializesNewCodesWhenFlagIsOff() throws Exception {
        ClubPosition.useLegacyApiNames(false);
        assertThat(objectMapper.writeValueAsString(ClubPosition.PRESIDENT)).isEqualTo("\"PRESIDENT\"");
    }

    @Test
    void deserializesBothForms() throws Exception {
        assertThat(objectMapper.readValue("\"ROLE_VICE_PRESIDENT\"", ClubPosition.class)).isEqualTo(ClubPosition.VICE_PRESIDENT);
        assertThat(objectMapper.readValue("\"VICE_PRESIDENT\"", ClubPosition.class)).isEqualTo(ClubPosition.VICE_PRESIDENT);
    }

    @Test
    void definesCapacityAndManagementRules() {
        assertThat(ClubPosition.BOARD_MEMBER.maxActiveHolders()).isEqualTo(7);
        assertThat(ClubPosition.PRESIDENT.maxActiveHolders()).isEqualTo(1);
        assertThat(ClubPosition.MEMBER.isManagement()).isFalse();
        assertThat(ClubPosition.TREASURER.isManagement()).isTrue();
    }
}
