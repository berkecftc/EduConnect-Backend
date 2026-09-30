package com.educonnect.courseservice.service;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCourseCodeTest {

    @Test
    void codesAreNormalizedIndependentlyOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.forLanguageTag("tr")}) {
                Locale.setDefault(locale);
                assertThat(CatalogCourseService.normalize("  bil101 ")).isEqualTo("BIL101");
                assertThat(CatalogCourseService.normalize("bil  101")).isEqualTo("BIL 101");
                assertThat(CatalogCourseService.normalize("BIL101")).isEqualTo("BIL101");
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
