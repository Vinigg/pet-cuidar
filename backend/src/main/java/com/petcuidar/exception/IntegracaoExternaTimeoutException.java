package com.petcuidar.exception;

/**
 * Uma integracao externa SEM fallback nao respondeu dentro do tempo limite
 * em nenhuma das tentativas (ex.: entrega manual de lembrete por e-mail).
 * Resposta: HTTP 504.
 *
 * Nao use para IA: lembrete, resumo e predicao tem fallback e respondem 200.
 * A mensagem vai para o cliente e nao deve conter dados pessoais.
 */
public class IntegracaoExternaTimeoutException extends RuntimeException {
    public IntegracaoExternaTimeoutException(String mensagem) {
        super(mensagem);
    }
}
