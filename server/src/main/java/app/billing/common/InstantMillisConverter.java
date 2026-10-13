package app.billing.common;

import java.time.Instant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores every Instant as epoch millis (INTEGER) — predictable in SQLite and easy to sync. */
@Converter(autoApply = true)
public class InstantMillisConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant value) {
        return value == null ? null : value.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long value) {
        return value == null ? null : Instant.ofEpochMilli(value);
    }
}
