package com.petcuidar.service.ia;

import com.petcuidar.model.Consulta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dados persistidos da consulta usados no lembrete. E a unica fonte de
 * verdade: o texto da IA e o template sao validados contra estes valores.
 * Contem apenas o necessario (sem e-mail, telefone ou CPF do tutor).
 */
public record DadosLembrete(String nomeTutor, String nomePet, String nomeVeterinario, LocalDateTime dataHora) {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public static DadosLembrete de(Consulta consulta) {
        var pet = consulta.getPet();
        var tutor = pet == null ? null : pet.getTutor();
        var veterinario = consulta.getVeterinario();
        return new DadosLembrete(
                limpar(tutor == null ? null : tutor.getNome()),
                limpar(pet == null ? null : pet.getNome()),
                limpar(veterinario == null ? null : veterinario.getNome()),
                consulta.getDataHora());
    }

    public String dataFormatada() { return dataHora.format(DATA); }

    public String horaFormatada() { return dataHora.format(HORA); }

    public boolean possuiVeterinario() { return nomeVeterinario != null; }

    private static String limpar(String valor) {
        if (valor == null) return null;
        String limpo = valor.strip().replaceAll("\\s+", " ");
        return limpo.isEmpty() ? null : limpo;
    }
}
