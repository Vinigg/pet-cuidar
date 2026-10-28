package com.petcuidar.service.ia;

/** Porta para o provedor de texto; no MVP é atendida pelo simulador local. */
public interface LembreteIaClient {
    String gerar(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario);
}
