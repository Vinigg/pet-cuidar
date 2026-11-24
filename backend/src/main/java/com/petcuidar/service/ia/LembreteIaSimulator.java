package com.petcuidar.service.ia;

/**
 * Simulador local (nao e integracao com LLM): permite rodar e demonstrar o
 * fluxo sem credenciais. Ativo quando ia.provider=simulador (padrao); o bean
 * e registrado por {@link com.petcuidar.config.IaClientConfig}.
 */
public class LembreteIaSimulator implements LembreteIaClient, ResumoIaClient {
    @Override
    public String gerar(DadosLembrete dados) {
        String comVeterinario = dados.possuiVeterinario() ? " com " + dados.nomeVeterinario() : "";
        return String.format("Olá, %s! Passando para lembrar que %s tem consulta marcada%s no dia %s às %s. Até lá!",
                dados.nomeTutor(), dados.nomePet(), comVeterinario, dados.dataFormatada(), dados.horaFormatada());
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
