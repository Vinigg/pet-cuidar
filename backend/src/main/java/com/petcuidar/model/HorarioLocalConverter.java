package com.petcuidar.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Converter
public class HorarioLocalConverter implements AttributeConverter<LocalTime, String> {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime horario) {
        return horario == null ? null : horario.format(FORMATO);
    }

    @Override
    public LocalTime convertToEntityAttribute(String horario) {
        return horario == null ? null : LocalTime.parse(horario, FORMATO);
    }
}