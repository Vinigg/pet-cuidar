package com.petcuidar.exception;

/**
 * Uma integracao externa SEM fallback falhou depois de todas as tentativas
 * (ex.: entrega manual de lembrete por e-mail). Resposta: HTTP 502.
 *
 * Nao use para IA: lembrete, resumo e predicao tem fallback e respondem 200.
 * A mensagem vai para o cliente e nao deve conter dados pessoais.
 */
public class IntegracaoExternaIndisponivelException extends RuntimeException {
    public IntegracaoExternaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
