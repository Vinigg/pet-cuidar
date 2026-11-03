package com.petcuidar.service.ia;

import com.petcuidar.exception.OperacaoNaoPermitidaException;
import com.petcuidar.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LembreteTemplateTest {
    private final LembreteTemplate template = new LembreteTemplate("Pet Cuidar");
    private final LembreteGuardrail guardrail = new LembreteGuardrail("Pet Cuidar");
    private static final LocalDateTime DATA = LocalDateTime.of(2026, 10, 28, 15, 30);

    private Consulta consulta(String tutorNome, String petNome, String vetNome, LocalDateTime dataHora) {
        Tutor tutor = new Tutor(); tutor.setNome(tutorNome); tutor.setEmail("ana@example.com");
        Pet pet = new Pet(); pet.setNome(petNome); pet.setTutor(tutor);
        Veterinario vet = new Veterinario(); vet.setNome(vetNome);
        Consulta c = new Consulta(); c.setPet(pet); c.setVeterinario(vet); c.setDataHora(dataHora);
        return c;
    }

    @Test void preencheTodosOsCamposComOsDadosReais() {
        String texto = template.montar(DadosLembrete.de(consulta("Ana", "Rex", "Dra. Bia", DATA)));
        assertEquals("Olá, Ana! Lembramos que Rex tem consulta agendada com Dra. Bia em 28/10/2026 às 15:30. "
                + "Contamos com a sua presença! Equipe Pet Cuidar.", texto);
    }

    @Test void textoDoTemplateSempreAprovadoPeloGuardrail() {
        DadosLembrete dados = DadosLembrete.de(consulta("  Ana   Souza ", "Rex", "Dr. Bruno Salgado", DATA));
        String texto = template.montar(dados);
        assertTrue(texto.contains("Ana Souza"));
        assertEquals(java.util.Optional.empty(), guardrail.avaliar(texto, dados));
    }

    @Test void omiteVeterinarioAusenteSemDeixarNullOuPlaceholder() {
        String texto = template.montar(DadosLembrete.de(consulta("Ana", "Rex", "  ", DATA)));
        assertFalse(texto.contains("null"));
        assertFalse(texto.contains("agendada com"));
        assertFalse(texto.matches(".*[{}\\[\\]<>].*"));
        assertTrue(texto.contains("28/10/2026 às 15:30"));
    }

    @Test void semNomeDaClinicaNaoAssina() {
        String texto = new LembreteTemplate(" ").montar(DadosLembrete.de(consulta("Ana", "Rex", "Dra. Bia", DATA)));
        assertFalse(texto.contains("Equipe"));
    }

    @Test void recusaDadosObrigatoriosAusentes() {
        var semTutor = assertThrows(OperacaoNaoPermitidaException.class,
                () -> template.montar(DadosLembrete.de(consulta(" ", "Rex", "Dra. Bia", DATA))));
        assertTrue(semTutor.getMessage().contains("nome do tutor"));
        assertThrows(OperacaoNaoPermitidaException.class,
                () -> template.montar(DadosLembrete.de(consulta("Ana", null, "Dra. Bia", DATA))));
        assertThrows(OperacaoNaoPermitidaException.class,
                () -> template.montar(DadosLembrete.de(consulta("Ana", "Rex", "Dra. Bia", null))));
        Consulta semPet = new Consulta(); semPet.setDataHora(DATA);
        assertThrows(OperacaoNaoPermitidaException.class, () -> template.montar(DadosLembrete.de(semPet)));
    }

    @Test void naoIncluiDadosPessoaisAlemDoNecessario() {
        String texto = template.montar(DadosLembrete.de(consulta("Ana", "Rex", "Dra. Bia", DATA)));
        assertFalse(texto.contains("ana@example.com"));
    }
}
