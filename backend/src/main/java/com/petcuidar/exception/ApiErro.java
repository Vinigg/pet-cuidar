package com.petcuidar.exception;

import java.time.LocalDateTime;

public class ApiErro {
    private int status;
    private String mensagem;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ApiErro(int status, String mensagem) {
        this.status = status;
        this.mensagem = mensagem;
    }

    public int getStatus() { return status; }
    public String getMensagem() { return mensagem; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
