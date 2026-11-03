package com.petcuidar.service.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulador local (nao e integracao com LLM): permite rodar e demonstrar o
 * fluxo sem credenciais. Ativo quando ia.llm.provider=simulador (padrao).
 */
@Component
@ConditionalOnProperty(name = "ia.llm.provider", havingValue = "simulador", matchIfMissing = true)
public class LembreteIaSimulator implements LembreteIaClient {
    @Override
    public String gerar(DadosLembrete dados) {
        String comVeterinario = dados.possuiVeterinario() ? " com " + dados.nomeVeterinario() : "";
        return String.format("Olá, %s! Passando para lembrar que %s tem consulta marcada%s no dia %s às %s. Até lá!",
                dados.nomeTutor(), dados.nomePet(), comVeterinario, dados.dataFormatada(), dados.horaFormatada());
    }
}
