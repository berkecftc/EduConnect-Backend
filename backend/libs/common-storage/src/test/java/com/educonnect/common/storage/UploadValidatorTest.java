package com.educonnect.common.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HTML = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

    private static UploadValidator validator(boolean enabled) {
        return new UploadValidator(new StorageProperties.Validation(enabled, DataSize.ofBytes(1024), DataSize.ofBytes(1024), DataSize.ofBytes(1024)));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    void disabled_shouldKeepClientValuesButSanitizeName() {
        ValidatedUpload result = validator(false).validate(file("../../x y.HTML", "text/html", HTML), UploadKind.IMAGE);

        assertThat(result.contentType()).isEqualTo("text/html");
        assertThat(result.safeOriginalName()).isEqualTo("x_y.HTML");
        assertThat(result.extension()).isEqualTo(".html");
    }

    @Test
    void image_shouldUseDetectedTypeInsteadOfClientClaims() {
        ValidatedUpload result = validator(true).validate(file("logo.gif", "text/html", PNG), UploadKind.IMAGE);

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo(".png");
    }

    @Test
    void image_shouldRejectHtmlDisguisedAsImage() {
        assertThatThrownBy(() -> validator(true).validate(file("logo.png", "image/png", HTML), UploadKind.IMAGE))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void image_shouldRejectPdf() {
        assertThatThrownBy(() -> validator(true).validate(file("a.pdf", "application/pdf", PDF), UploadKind.IMAGE))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void document_shouldAcceptPdfAndImages() {
        assertThat(validator(true).validate(file("belge.pdf", "application/pdf", PDF), UploadKind.DOCUMENT).extension())
                .isEqualTo(".pdf");
        assertThat(validator(true).validate(file("kimlik.jpeg", "image/jpeg", JPEG), UploadKind.DOCUMENT).extension())
                .isEqualTo(".jpg");
    }

    @Test
    void attachment_shouldRejectActiveContentExtensions() {
        assertThatThrownBy(() -> validator(true).validate(file("odev.html", "text/html", HTML), UploadKind.ATTACHMENT))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> validator(true).validate(file("cizim.svg", "image/svg+xml", HTML), UploadKind.ATTACHMENT))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void attachment_shouldStoreUnknownTypesAsOctetStream() {
        ValidatedUpload result = validator(true).validate(
                file("odev.docx", "application/vnd.openxmlformats", new byte[]{'P', 'K', 3, 4, 0}), UploadKind.ATTACHMENT);

        assertThat(result.contentType()).isEqualTo(UploadValidator.OCTET_STREAM);
        assertThat(result.extension()).isEqualTo(".docx");
    }

    @Test
    void shouldRejectOversizedAndEmptyFiles() {
        assertThatThrownBy(() -> validator(true).validate(file("big.png", "image/png", new byte[2048]), UploadKind.IMAGE))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("MB");
        assertThatThrownBy(() -> validator(false).validate(file("empty.png", "image/png", new byte[0]), UploadKind.IMAGE))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void attachmentHeader_shouldNotAllowHeaderInjection() {
        String header = SafeFileNames.attachmentHeader("rapor\"\r\nX-Evil: 1.pdf");

        assertThat(header).doesNotContain("\r").doesNotContain("\n").startsWith("attachment;");
        assertThat(SafeFileNames.sanitize("Ödev Raporu (son).pdf")).isEqualTo("Ödev_Raporu_son.pdf");
    }
}
