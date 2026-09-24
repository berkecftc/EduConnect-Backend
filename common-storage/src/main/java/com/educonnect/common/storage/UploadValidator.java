package com.educonnect.common.storage;

import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

public class UploadValidator {

    static final String OCTET_STREAM = "application/octet-stream";

    private static final Set<String> BLOCKED_ATTACHMENT_EXTENSIONS = Set.of(
            ".html", ".htm", ".xhtml", ".svg", ".svgz", ".js", ".mjs", ".xml", ".xsl", ".swf",
            ".exe", ".dll", ".msi", ".bat", ".cmd", ".com", ".scr", ".ps1", ".vbs", ".sh", ".jar", ".apk", ".php", ".jsp");

    private final StorageProperties.Validation settings;

    public UploadValidator(StorageProperties.Validation settings) {
        this.settings = settings;
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public ValidatedUpload validate(MultipartFile file, UploadKind kind) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Dosya boş olamaz.");
        }
        String safeName = SafeFileNames.sanitize(file.getOriginalFilename());
        String originalExtension = SafeFileNames.extensionOf(safeName);
        if (!settings.enabled()) {
            return new ValidatedUpload(file.getContentType(), originalExtension, safeName);
        }

        DataSize limit = switch (kind) {
            case IMAGE -> settings.maxImageSize();
            case DOCUMENT -> settings.maxDocumentSize();
            case ATTACHMENT -> settings.maxAttachmentSize();
        };
        if (file.getSize() > limit.toBytes()) {
            throw new InvalidUploadException("Dosya boyutu en fazla " + limit.toMegabytes() + " MB olabilir.");
        }

        DetectedType detected = DetectedType.detect(readHeader(file));
        return switch (kind) {
            case IMAGE -> {
                if (detected == null || !detected.image()) {
                    throw new InvalidUploadException("Yalnızca JPEG, PNG, GIF veya WEBP görseller yüklenebilir.");
                }
                yield new ValidatedUpload(detected.contentType(), detected.extension(), safeName);
            }
            case DOCUMENT -> {
                if (detected == null || !(detected.image() || detected == DetectedType.PDF)) {
                    throw new InvalidUploadException("Yalnızca PDF, JPEG, PNG, GIF veya WEBP dosyalar yüklenebilir.");
                }
                yield new ValidatedUpload(detected.contentType(), detected.extension(), safeName);
            }
            case ATTACHMENT -> {
                if (BLOCKED_ATTACHMENT_EXTENSIONS.contains(originalExtension)) {
                    throw new InvalidUploadException("Bu dosya türü yüklenemez: " + originalExtension);
                }
                if (detected != null && (detected.image() || detected == DetectedType.PDF)) {
                    yield new ValidatedUpload(detected.contentType(), detected.extension(), safeName);
                }
                yield new ValidatedUpload(OCTET_STREAM, originalExtension, safeName);
            }
        };
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(16);
            return Arrays.copyOf(header, header.length);
        } catch (IOException e) {
            throw new InvalidUploadException("Dosya okunamadı.");
        }
    }

    enum DetectedType {
        JPEG("image/jpeg", ".jpg", true),
        PNG("image/png", ".png", true),
        GIF("image/gif", ".gif", true),
        WEBP("image/webp", ".webp", true),
        PDF("application/pdf", ".pdf", false);

        private final String contentType;
        private final String extension;
        private final boolean image;

        DetectedType(String contentType, String extension, boolean image) {
            this.contentType = contentType;
            this.extension = extension;
            this.image = image;
        }

        String contentType() {
            return contentType;
        }

        String extension() {
            return extension;
        }

        boolean image() {
            return image;
        }

        static DetectedType detect(byte[] h) {
            if (startsWith(h, 0xFF, 0xD8, 0xFF)) {
                return JPEG;
            }
            if (startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
                return PNG;
            }
            if (startsWith(h, 'G', 'I', 'F', '8') && h.length >= 6 && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
                return GIF;
            }
            if (startsWith(h, 'R', 'I', 'F', 'F') && h.length >= 12
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
                return WEBP;
            }
            if (startsWith(h, '%', 'P', 'D', 'F', '-')) {
                return PDF;
            }
            return null;
        }

        private static boolean startsWith(byte[] data, int... prefix) {
            if (data.length < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if ((data[i] & 0xFF) != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
