package com.walden.cvect.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("FloatArrayTextConverter tests")
class FloatArrayTextConverterTest {

    private final FloatArrayTextConverter converter = new FloatArrayTextConverter();

    @Test
    @DisplayName("should serialize float arrays to postgres text format")
    void shouldSerializeArray() {
        assertEquals("{0.1,0.2,1.0}", converter.convertToDatabaseColumn(new float[] {0.1f, 0.2f, 1.0f}));
    }

    @Test
    @DisplayName("should deserialize brace or bracket text formats")
    void shouldDeserializeKnownFormats() {
        assertArrayEquals(new float[] {0.1f, 0.2f, 1.0f}, converter.convertToEntityAttribute("{0.1,0.2,1.0}"));
        assertArrayEquals(new float[] {0.3f, 0.4f}, converter.convertToEntityAttribute("[0.3, 0.4]"));
    }

    @Test
    @DisplayName("should treat blank values as null")
    void shouldTreatBlankAsNull() {
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute(""));
        assertNull(converter.convertToEntityAttribute("  "));
        assertNull(converter.convertToEntityAttribute("{}"));
        assertNull(converter.convertToEntityAttribute("[]"));
    }
}
