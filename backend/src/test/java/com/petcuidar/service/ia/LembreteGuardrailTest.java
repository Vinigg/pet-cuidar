package com.petcuidar.service.ia;

import com.petcuidar.service.ia.LembreteGuardrail.Motivo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LembreteGuardrailTest {
    private final LembreteGuardrail guardrail = new LembreteGuardrail("Pet Cuidar");
    private final DadosLembrete dados = new DadosLembrete("Ana", "Rex", "Dra. Bia Lopes",
            LocalDateTime.of(2026, 10, 28, 15, 30));

    private Optional<Motivo> avaliar(String texto) { return guardrail.avaliar(texto, dados); }

    @Test void aprovaTextoComDadosCorretos() {
        assertEquals(Optional.empty(), avaliar(
                "Olá, Ana! Rex tem consulta com a Dra. Bia Lopes em 28/10/2026 às 15:30. Equipe Pet Cuidar."));
        assertEquals(Optional.empty(), avaliar("Ana, lembrete: Rex, 28/10/2026, 15:30."));
    }

    @Test void rejeitaVazioELongo() {
        assertEquals(Optional.of(Motivo.VAZIO), avaliar(null));
        assertEquals(Optional.of(Motivo.VAZIO), avaliar("   "));
        assertEquals(Optional.of(Motivo.MUITO_LONGO),
                avaliar("Ana Rex 28/10/2026 15:30 " + "a".repeat(LembreteGuardrail.TAMANHO_MAXIMO)));
    }

    @Test void rejeitaCamposOmitidos() {
        assertEquals(Optional.of(Motivo.TUTOR_AUSENTE), avaliar("Rex tem consulta em 28/10/2026 às 15:30."));
        assertEquals(Optional.of(Motivo.PET_AUSENTE), avaliar("Ana, consulta em 28/10/2026 às 15:30."));
        assertEquals(Optional.of(Motivo.DATA_AUSENTE), avaliar("Ana, Rex tem consulta amanhã às 15:30."));
        assertEquals(Optional.of(Motivo.HORARIO_AUSENTE), avaliar("Ana, Rex tem consulta em 28/10/2026."));
    }

    @Test void rejeitaTrocaDeNome() {
        assertEquals(Optional.of(Motivo.TUTOR_AUSENTE), avaliar("Olá, Ane! Rex: 28/10/2026 às 15:30."));
        assertEquals(Optional.of(Motivo.PET_AUSENTE), avaliar("Olá, Ana! Max: 28/10/2026 às 15:30."));
    }

    @Test void rejeitaFormatosInconsistentes() {
        assertEquals(Optional.of(Motivo.HORARIO_AUSENTE), avaliar("Ana, Rex: 28/10/2026 às 15h30."));
        assertEquals(Optional.of(Motivo.DATA_AUSENTE), avaliar("Ana, Rex: 28/10 às 15:30."));
        assertEquals(Optional.of(Motivo.DATA_AUSENTE), avaliar("Ana, Rex: 2026-10-28 às 15:30."));
    }

    @Test void rejeitaInformacoesNaoFornecidas() {
        assertEquals(Optional.of(Motivo.INFORMACAO_NAO_FORNECIDA),
                avaliar("Ana, Rex: 28/10/2026 às 15:30 (ou 29/10/2026 às 16:00)."));
        assertEquals(Optional.of(Motivo.INFORMACAO_NAO_FORNECIDA),
                avaliar("Ana, Rex: 28/10/2026 às 15:30. Chegue 15 minutos antes."));
        assertEquals(Optional.of(Motivo.INFORMACAO_NAO_FORNECIDA),
                avaliar("Ana, Rex: 28/10/2026 às 15:30. Dúvidas: (85) 99999-0000."));
        assertEquals(Optional.of(Motivo.INFORMACAO_NAO_FORNECIDA),
                avaliar("Ana, Rex: 28/10/2026 às 15:30. Valor R$ 120."));
        assertEquals(Optional.of(Motivo.CONTATO_OU_LINK),
                avaliar("Ana, Rex: 28/10/2026 às 15:30. Veja https://exemplo.com"));
        assertEquals(Optional.of(Motivo.CONTATO_OU_LINK),
                avaliar("Ana, Rex: 28/10/2026 às 15:30. Escreva para contato@clinica.com"));
        assertEquals(Optional.of(Motivo.VETERINARIO_DIVERGENTE),
                avaliar("Ana, Rex tem consulta com o Dr. Carlos em 28/10/2026 às 15:30."));
    }

    @Test void rejeitaPlaceholdersNaoSubstituidos() {
        assertEquals(Optional.of(Motivo.PLACEHOLDER), avaliar("Olá {tutor}, Ana Rex 28/10/2026 15:30"));
        assertEquals(Optional.of(Motivo.PLACEHOLDER), avaliar("Olá [NOME], Ana Rex 28/10/2026 15:30"));
        assertEquals(Optional.of(Motivo.PLACEHOLDER), avaliar("Olá null, Ana Rex 28/10/2026 15:30"));
    }
}
