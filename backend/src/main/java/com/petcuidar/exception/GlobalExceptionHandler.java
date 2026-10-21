package com.petcuidar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Ponto unico de tratamento de erros da API (Secao 3.4 do documento):
 * 400 para dados invalidos, 404 para recurso inexistente, 409 para
 * conflito de agenda. Falhas de IA NUNCA chegam aqui como erro 5xx para o
 * usuario final - elas sao resolvidas com fallback dentro dos services de IA.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErro> handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiErro(400, mensagem));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiErro> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErro(404, ex.getMessage()));
    }

    @ExceptionHandler(ConflitoHorarioException.class)
    public ResponseEntity<ApiErro> handleConflito(ConflitoHorarioException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErro(409, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErro> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiErro(400, ex.getMessage()));
    }
}
