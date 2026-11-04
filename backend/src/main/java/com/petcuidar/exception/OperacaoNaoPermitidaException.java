package com.petcuidar.exception;

/**
 * Operacao recusada pelo estado atual da consulta (ex.: reagendar consulta
 * finalizada ou enviar lembrete de consulta cancelada). Responde HTTP 409.
 */
public class OperacaoNaoPermitidaException extends RuntimeException {
    public OperacaoNaoPermitidaException(String message) { super(message); }
}
