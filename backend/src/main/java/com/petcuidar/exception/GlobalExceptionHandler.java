package com.petcuidar.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

/**
 * Ponto unico de tratamento de erros da API (Secao 3.4 do documento). Todo
 * erro sai no formato {@link ApiErro}: {@code {status, mensagem, timestamp}}.
 *
 * <table>
 *   <caption>Quando cada codigo e usado</caption>
 *   <tr><th>HTTP</th><th>Quando</th></tr>
 *   <tr><td>400</td><td>Dados invalidos: validacao do corpo, parametro ausente
 *       ou de tipo errado, JSON malformado, regra de entrada (IllegalArgumentException).</td></tr>
 *   <tr><td>404</td><td>Recurso inexistente (RecursoNaoEncontradoException).</td></tr>
 *   <tr><td>409</td><td>Conflito de agenda, reserva concorrente, cadastro duplicado,
 *       transicao de status invalida ou operacao recusada pelo estado da consulta.</td></tr>
 *   <tr><td>500</td><td>Erro inesperado. Mensagem generica; detalhes so no log.</td></tr>
 *   <tr><td>502</td><td>Integracao externa SEM fallback falhou apos todas as tentativas
 *       (IntegracaoExternaIndisponivelException; ex.: envio manual de lembrete por e-mail).</td></tr>
 *   <tr><td>504</td><td>Integracao externa SEM fallback estourou o tempo limite
 *       (IntegracaoExternaTimeoutException).</td></tr>
 * </table>
 *
 * Falhas de IA (lembrete, resumo e predicao) NUNCA chegam aqui como 5xx:
 * elas sao resolvidas com fallback dentro dos services de IA e a resposta
 * e 200 com o indicador de fallback.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String MENSAGEM_ERRO_INTERNO =
            "Erro interno ao processar a requisicao. Tente novamente mais tarde.";

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

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErro> handleConcorrencia(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErro(409,
                "Este horario acabou de ser reservado por outra requisicao. Escolha outro horario."));
    }

    @ExceptionHandler(CadastroConflitanteException.class)
    public ResponseEntity<ApiErro> handleCadastroConflitante(CadastroConflitanteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErro(409, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErro> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiErro(400, ex.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErro> handleRequisicaoInvalida(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiErro(400, "Requisicao invalida ou malformada."));
    }

    @ExceptionHandler(TransicaoEstadoInvalidaException.class)
    public ResponseEntity<ApiErro> handleTransicao(TransicaoEstadoInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErro(409, ex.getMessage()));
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ResponseEntity<ApiErro> handleOperacaoNaoPermitida(OperacaoNaoPermitidaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErro(409, ex.getMessage()));
    }

    @ExceptionHandler(IntegracaoExternaIndisponivelException.class)
    public ResponseEntity<ApiErro> handleIntegracaoIndisponivel(IntegracaoExternaIndisponivelException ex) {
        log.warn("Integracao externa indisponivel: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiErro(502, ex.getMessage()));
    }

    @ExceptionHandler(IntegracaoExternaTimeoutException.class)
    public ResponseEntity<ApiErro> handleIntegracaoTimeout(IntegracaoExternaTimeoutException ex) {
        log.warn("Integracao externa sem resposta no tempo limite: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new ApiErro(504, ex.getMessage()));
    }

    /**
     * Ultimo recurso. Excecoes do proprio Spring MVC que ja carregam um status
     * (405, 404 de rota inexistente, 415...) mantem esse status; o resto vira
     * 500 com mensagem generica - sem stack trace nem dados pessoais na
     * resposta. O erro completo fica somente no log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErro> handleInesperado(Exception ex) {
        HttpStatusCode status = statusDeclarado(ex);
        if (status != null && !status.is5xxServerError()) {
            return ResponseEntity.status(status).body(new ApiErro(status.value(), mensagemPadrao(status)));
        }
        log.error("Erro inesperado ao processar a requisicao", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErro(500, MENSAGEM_ERRO_INTERNO));
    }

    private static HttpStatusCode statusDeclarado(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) return errorResponse.getStatusCode();
        ResponseStatus anotacao = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        return anotacao == null ? null : anotacao.code();
    }

    private static String mensagemPadrao(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Recurso nao encontrado.";
            case 405 -> "Metodo HTTP nao suportado para este recurso.";
            case 406 -> "Formato de resposta solicitado nao suportado.";
            case 415 -> "Tipo de conteudo nao suportado.";
            default -> "Requisicao invalida ou malformada.";
        };
    }
}
