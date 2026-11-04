package com.petcuidar.service;

import com.petcuidar.model.HorarioAtendimentoVeterinario;
import com.petcuidar.model.Veterinario;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class HorarioAtendimentoVeterinarioTest {
    private final LocalDate segunda = LocalDate.of(2026, 11, 9);

    @Test
    void consultaInteiraDeveCaberNoPeriodoEDia() {
        var periodo = new HorarioAtendimentoVeterinario(new Veterinario(), DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        assertTrue(periodo.atende(segunda.atTime(8, 0)));
        assertTrue(periodo.atende(segunda.atTime(11, 30)));
        assertFalse(periodo.atende(segunda.atTime(12, 0)));
        assertFalse(periodo.atende(segunda.atTime(7, 30)));
        assertFalse(periodo.atende(segunda.atTime(8, 15)));
        assertFalse(periodo.atende(segunda.plusDays(1).atTime(8, 0)));
    }

    @Test
    void rejeitaPeriodosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> periodo(LocalTime.NOON, LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> periodo(LocalTime.NOON, LocalTime.of(8, 0)));
        assertThrows(IllegalArgumentException.class, () -> periodo(LocalTime.of(8, 15), LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> periodo(null, LocalTime.NOON));
    }

    private HorarioAtendimentoVeterinario periodo(LocalTime inicio, LocalTime fim) {
        return new HorarioAtendimentoVeterinario(new Veterinario(), DayOfWeek.MONDAY, inicio, fim);
    }
}