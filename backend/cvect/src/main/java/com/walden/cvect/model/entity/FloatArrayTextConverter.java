package com.walden.cvect.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class FloatArrayTextConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(attribute[i]);
        }
        builder.append('}');
        return builder.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String normalized = dbData.trim();
        if (normalized.isEmpty() || "{}".equals(normalized) || "[]".equals(normalized)) {
            return null;
        }
        if ((normalized.startsWith("{") && normalized.endsWith("}"))
                || (normalized.startsWith("[") && normalized.endsWith("]"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            values.add(Float.parseFloat(token));
        }
        if (values.isEmpty()) {
            return null;
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
