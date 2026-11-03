package com.petcuidar.service.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ia.provider", havingValue = "simulator", matchIfMissing = true)
public class LembreteIaSimulator implements LembreteIaClient, ResumoIaClient {
    @Override
    public String gerar(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario) {
        return String.format("Ola %s! Passando para lembrar que %s tem consulta marcada com %s no dia %s. Ate la!",
                nomeTutor, nomePet, veterinario, dataHoraFmt);
    }

    @Override
    public String resumir(String nomePet, java.util.List<String> atendimentos, String itensCriticos) {
        if (atendimentos.isEmpty()) {
            return nomePet + " ainda nao possui atendimentos anteriores registrados.";
        }
        return String.format("%s possui %d atendimento(s) registrado(s). Mais recentes: %s.",
                nomePet, atendimentos.size(), String.join("; ", atendimentos.stream().limit(3).toList()));
    }
}
