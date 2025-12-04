package org.devaxiom.safedocs.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.devaxiom.safedocs.exception.JsonConversionException;

public final class JsonHelper {
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private JsonHelper() {
    }

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonConversionException("JSON conversion failed", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new JsonConversionException("JSON parsing failed", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new JsonConversionException("JSON parsing failed", e);
        }
    }
}
