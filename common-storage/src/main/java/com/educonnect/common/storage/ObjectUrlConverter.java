package com.educonnect.common.storage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

@Converter
public class ObjectUrlConverter implements AttributeConverter<String, String> {

    private final StorageUrls storageUrls;

    public ObjectUrlConverter() {
        this(StorageUrls.defaults());
    }

    @Autowired
    public ObjectUrlConverter(StorageUrls storageUrls) {
        this.storageUrls = storageUrls;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return storageUrls.toStoredValue(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return storageUrls.toUrl(dbData);
    }
}
