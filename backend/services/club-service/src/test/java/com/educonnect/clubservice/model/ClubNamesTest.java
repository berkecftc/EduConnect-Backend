package com.educonnect.clubservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClubNamesTest {

    @Test
    void namesThatDifferOnlyInCaseTurkishLettersOrSpacingAreTheSame() {
        String expected = "yapay zeka kulubu";
        assertThat(ClubNames.normalize("Yapay Zeka Kulübü")).isEqualTo(expected);
        assertThat(ClubNames.normalize("YAPAY ZEKA KULÜBÜ")).isEqualTo(expected);
        assertThat(ClubNames.normalize("  Yapay   Zekâ Kulubu ")).isEqualTo(expected);
    }

    @Test
    void dottedAndDotlessIAreFoldedTheSameWay() {
        assertThat(ClubNames.normalize("IŞIK İletişim")).isEqualTo(ClubNames.normalize("ışık iletişim"));
        assertThat(ClubNames.normalize("Çağ Öğrenci Şöleni")).isEqualTo("cag ogrenci soleni");
    }

    @Test
    void settingTheNameAlsoSetsTheNormalizedName() {
        Club club = new Club();
        club.setName("Müzik Topluluğu");
        assertThat(club.getNormalizedName()).isEqualTo("muzik toplulugu");
    }
}
