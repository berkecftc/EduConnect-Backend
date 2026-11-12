package com.educonnect.common.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorageUrlsTest {

    private final StorageUrls urls = new StorageUrls("https://files.educonnect.edu/", List.of("http://minio.internal:9000"));

    @Test
    void toStoredValue_shouldStripKnownBasesAndQuery() {
        assertThat(urls.toStoredValue("http://localhost:9000/club-bucket/logos/a.png")).isEqualTo("club-bucket/logos/a.png");
        assertThat(urls.toStoredValue("https://files.educonnect.edu/club-bucket/logos/a.png")).isEqualTo("club-bucket/logos/a.png");
        assertThat(urls.toStoredValue("http://minio.internal:9000/b/x.pdf?X-Amz-Signature=abc")).isEqualTo("b/x.pdf");
    }

    @Test
    void toStoredValue_shouldKeepExternalUrlsAndKeysUntouched() {
        assertThat(urls.toStoredValue("https://ui-avatars.com/api/?name=A")).isEqualTo("https://ui-avatars.com/api/?name=A");
        assertThat(urls.toStoredValue("club-bucket/logos/a.png")).isEqualTo("club-bucket/logos/a.png");
        assertThat(urls.toStoredValue(null)).isNull();
    }

    @Test
    void toUrl_shouldBuildFromCurrentPublicBaseIncludingLegacyRows() {
        assertThat(urls.toUrl("club-bucket/logos/a.png")).isEqualTo("https://files.educonnect.edu/club-bucket/logos/a.png");
        assertThat(urls.toUrl("http://localhost:9000/club-bucket/logos/a.png"))
                .isEqualTo("https://files.educonnect.edu/club-bucket/logos/a.png");
        assertThat(urls.toUrl("https://ui-avatars.com/api/?name=A")).isEqualTo("https://ui-avatars.com/api/?name=A");
        assertThat(urls.toUrl(null)).isNull();
    }

    @Test
    void locate_shouldSplitBucketAndObjectForOwnStorageOnly() {
        assertThat(urls.locate("http://localhost:9000/academician-id-cards/student-documents/u_doc.pdf"))
                .contains(new StorageUrls.StoredObject("academician-id-cards", "student-documents/u_doc.pdf"));
        assertThat(urls.locate("club-bucket/logos/a.png")).contains(new StorageUrls.StoredObject("club-bucket", "logos/a.png"));
        assertThat(urls.locate("https://ui-avatars.com/api/?name=A")).isEmpty();
        assertThat(urls.locate("a.png")).isEmpty();
        assertThat(urls.locate(null)).isEmpty();
    }

    @Test
    void objectName_shouldResolveKeyInsideBucket() {
        assertThat(urls.objectName("http://localhost:9000/club-bucket/logos/a.png", "club-bucket")).isEqualTo("logos/a.png");
        assertThat(urls.objectName("club-bucket/logos/a.png", "club-bucket")).isEqualTo("logos/a.png");
        assertThat(urls.objectName("logos/a.png", "club-bucket")).isEqualTo("logos/a.png");
    }

    @Test
    void url_shouldJoinBaseBucketAndKey() {
        assertThat(urls.url("events-bucket", "/events/x.jpg")).isEqualTo("https://files.educonnect.edu/events-bucket/events/x.jpg");
    }

    @Test
    void converter_shouldStoreKeyAndReadFullUrl() {
        ObjectUrlConverter converter = new ObjectUrlConverter(urls);

        String stored = converter.convertToDatabaseColumn("https://files.educonnect.edu/courses-bucket/c.png");

        assertThat(stored).isEqualTo("courses-bucket/c.png");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("https://files.educonnect.edu/courses-bucket/c.png");
    }
}
