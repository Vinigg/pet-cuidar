package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.model.Pet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResumoHistoricoServiceTest {

    @Test
    void incluiAlergiaCriticaPorRegraFixaMesmoSeIaOmitir() {
        Pet pet = pet("Rex", "Alergia grave a dipirona");
        ResumoHistoricoService service = service(
                (nome, historico, criticos) -> "Rex realizou consulta de rotina.", 500);
        try {
            var dto = service.gerar(pet, List.of(atendimento("Consulta de rotina")));
            String resumo = dto.getResumo();
            assertTrue(resumo.contains("Rex realizou consulta de rotina."));
            assertTrue(resumo.contains("ATENCAO - itens criticos: Alergia grave a dipirona"));
            assertTrue(dto.getItensCriticos().contains("dipirona"));
            assertFalse(dto.isUsouFallback());
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void falhaDoProvedorUsaHistoricoBrutoComoFallback() {
        Pet pet = pet("Luna", null);
        ResumoHistoricoService service = service((nome, historico, criticos) -> {
            throw new IllegalStateException("provedor fora do ar");
        }, 500);
        try {
            var dto = service.gerar(pet, List.of(atendimento("Vacina anual aplicada")));
            String resumo = dto.getResumo();
            assertTrue(resumo.contains("Vacina anual aplicada"));
            assertTrue(dto.isUsouFallback());
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void timeoutUsaFallbackSemBloquearHistorico() {
        Pet pet = pet("Nina", "Reacao adversa a antibiotico");
        ResumoHistoricoService service = service((nome, historico, criticos) -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "resposta tardia";
        }, 20);
        try {
            String resumo = service.gerar(pet, List.of(atendimento("Atendimento emergencial"))).getResumo();
            assertTrue(resumo.contains("Atendimento emergencial"));
            assertTrue(resumo.contains("Reacao adversa a antibiotico"));
        } finally {
            service.encerrarExecutor();
        }
    }

    private ResumoHistoricoService service(ResumoIaClient client, long timeoutMs) {
        IaProperties props = new IaProperties();
        props.setTimeoutMs(timeoutMs);
        return new ResumoHistoricoService(client, props);
    }

    private Pet pet(String nome, String alergias) {
        Pet pet = new Pet();
        pet.setNome(nome);
        pet.setAlergiasCriticas(alergias);
        return pet;
    }

    private HistoricoAtendimento atendimento(String descricao) {
        HistoricoAtendimento atendimento = new HistoricoAtendimento();
        atendimento.setDescricao(descricao);
        return atendimento;
    }
}
