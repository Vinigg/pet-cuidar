package com.petcuidar.service.ia;

import java.util.List;

/** Porta para resumir o histórico sem acoplar a regra de negócio ao provedor de LLM. */
public interface ResumoIaClient {
    String resumir(String nomePet, List<String> atendimentos, String itensCriticos);
}
