package com.petcuidar.exception;

public class CadastroConflitanteException extends RuntimeException {
    public CadastroConflitanteException(String mensagem) {
        super(mensagem);
    }

    public CadastroConflitanteException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
