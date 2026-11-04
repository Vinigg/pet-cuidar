package com.petcuidar.service.ia;

import com.petcuidar.exception.OperacaoNaoPermitidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Template deterministico de lembrete (PC-032). Usado sempre que a IA falha,
 * expira, esta com o circuito aberto ou tem a resposta rejeitada pelo
 * guardrail. So usa dados persistidos da consulta; trechos opcionais
 * (veterinario, clinica) sao omitidos quando vazios, nunca preenchidos com
 * "null" ou placeholders.
 */
@Component
public class LembreteTemplate {

    private final String nomeClinica;

    public LembreteTemplate(@Value("${lembrete.nome-clinica:Pet Cuidar}") String nomeClinica) {
        this.nomeClinica = nomeClinica == null || nomeClinica.isBlank() ? null : nomeClinica.strip();
    }

    public String nomeClinica() { return nomeClinica; }

    public String montar(DadosLembrete dados) {
        validarObrigatorios(dados);

        StringBuilder texto = new StringBuilder()
                .append("Olá, ").append(dados.nomeTutor()).append("! ")
                .append("Lembramos que ").append(dados.nomePet()).append(" tem consulta agendada");
        if (dados.possuiVeterinario()) {
            texto.append(" com ").append(dados.nomeVeterinario());
        }
        texto.append(" em ").append(dados.dataFormatada())
                .append(" às ").append(dados.horaFormatada()).append(". ")
                .append("Contamos com a sua presença!");
        if (nomeClinica != null) {
            texto.append(" Equipe ").append(nomeClinica).append('.');
        }
        return texto.toString();
    }

    /** Campos minimos exigidos pela PC-032: tutor, pet e data/hora. */
    public static void validarObrigatorios(DadosLembrete dados) {
        List<String> ausentes = new ArrayList<>();
        if (dados == null || dados.nomeTutor() == null) ausentes.add("nome do tutor");
        if (dados == null || dados.nomePet() == null) ausentes.add("nome do pet");
        if (dados == null || dados.dataHora() == null) ausentes.add("data e hora");
        if (!ausentes.isEmpty()) {
            throw new OperacaoNaoPermitidaException(
                    "Nao e possivel gerar o lembrete: dados obrigatorios ausentes (" + String.join(", ", ausentes) + ").");
        }
    }
}
