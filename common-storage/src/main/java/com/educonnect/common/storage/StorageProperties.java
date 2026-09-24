package com.educonnect.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

@ConfigurationProperties(prefix = "educonnect.storage")
public record StorageProperties(String publicBaseUrl,
                                List<String> legacyBaseUrls,
                                Validation validation) {

    public StorageProperties {
        legacyBaseUrls = legacyBaseUrls != null ? List.copyOf(legacyBaseUrls) : List.of();
        validation = validation != null ? validation : new Validation(false, null, null, null);
    }

    public record Validation(boolean enabled, DataSize maxImageSize, DataSize maxDocumentSize, DataSize maxAttachmentSize) {

        public Validation {
            maxImageSize = maxImageSize != null ? maxImageSize : DataSize.ofMegabytes(5);
            maxDocumentSize = maxDocumentSize != null ? maxDocumentSize : DataSize.ofMegabytes(10);
            maxAttachmentSize = maxAttachmentSize != null ? maxAttachmentSize : DataSize.ofMegabytes(20);
        }
    }
}
