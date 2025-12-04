package org.devaxiom.safedocs.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.devaxiom.safedocs.exception.JsonConversionException;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

@Converter
public class JsonbStringConverter implements AttributeConverter<String, Object> {

    @Override
    public Object convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        try {
            pg.setValue(attribute);
        } catch (SQLException e) {
            throw new JsonConversionException("Failed to convert String to PGobject for JSONB", e);
        }
        return pg;
    }

    @Override
    public String convertToEntityAttribute(Object dbData) {
        if (dbData instanceof PGobject) {
            return ((PGobject) dbData).getValue();
        }
        return null;
    }
}
