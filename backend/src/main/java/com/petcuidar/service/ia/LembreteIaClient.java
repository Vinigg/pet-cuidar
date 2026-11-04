package com.petcuidar.service.ia;

/**
 * Porta para o provedor de texto dos lembretes. Implementacoes:
 * {@link LembreteIaSimulator} (padrao, sem credenciais) e
 * {@link AnthropicLembreteIaClient} (ia.llm.provider=anthropic).
 * O retorno e sempre tratado como saida bruta: o gerador aplica timeout,
 * circuit breaker e guardrail antes de qualquer uso.
 */
public interface LembreteIaClient {

    /** @throws RespostaIaInvalidaException quando o provedor responde sem texto utilizavel. */
    String gerar(DadosLembrete dados);
}
