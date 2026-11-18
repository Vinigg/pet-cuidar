package com.petcuidar.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.petcuidar.auth.SessaoService.COOKIE;
import static com.petcuidar.auth.SessaoService.COOKIE_CADASTRO;
import static com.petcuidar.auth.SessaoService.normalizarEmail;

@RestController
@RequestMapping("/auth")
public class AuthController {
    public static final String CABECALHO = "X-Pet-Cuidar";
    private static final String MENSAGEM_ENVIO = "Se o endereço estiver apto, você receberá um código por e-mail.";
    private static final String MENSAGEM_CODIGO = "Código inválido ou expirado.";
    private static final int REQUISICOES_POR_MINUTO = 30;

    public record Solicitacao(@NotBlank @Email @Size(max = 254) String email,
                              @NotNull DesafioEmailService.Finalidade finalidade) {}
    public record Verificacao(@NotBlank @Email @Size(max = 254) String email,
                              @NotNull DesafioEmailService.Finalidade finalidade,
                              @NotNull @Pattern(regexp = "[0-9]{8}") String codigo) {}
    private record Limite(long minuto, int total) {}

    private final DesafioEmailService desafios;
    private final SessaoService sessoes;
    private final boolean cookieSeguro;
    private final Map<String, Limite> limites = new HashMap<>();

    public AuthController(DesafioEmailService desafios, SessaoService sessoes,
                          @Value("${app.auth.secure-cookie:true}") boolean cookieSeguro) {
        this.desafios = desafios;
        this.sessoes = sessoes;
        this.cookieSeguro = cookieSeguro;
    }

    @PostMapping(value = "/codigo", headers = CABECALHO + "=1")
    public ResponseEntity<?> solicitar(@Valid @RequestBody Solicitacao pedido, HttpServletRequest request) {
        if (limitar(request.getRemoteAddr())) {
            return resposta(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("mensagem", "Aguarde antes de tentar novamente."));
        }
        desafios.solicitar(normalizarEmail(pedido.email()), pedido.finalidade());
        return resposta(HttpStatus.ACCEPTED).body(Map.of("mensagem", MENSAGEM_ENVIO));
    }

    @PostMapping(value = "/verificar", headers = CABECALHO + "=1")
    public ResponseEntity<?> verificar(@Valid @RequestBody Verificacao pedido, HttpServletRequest request,
                                       @CookieValue(name = COOKIE, required = false) String tokenAnterior) {
        if (limitar(request.getRemoteAddr())) {
            return resposta(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("mensagem", "Aguarde antes de tentar novamente."));
        }
        var credencial = sessoes.verificar(normalizarEmail(pedido.email()), pedido.finalidade(), pedido.codigo());
        if (credencial.isEmpty()) {
            return resposta(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", MENSAGEM_CODIGO));
        }
        var emitida = credencial.get();
        if (emitida.conta() == null) {
            return resposta(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, cookie(COOKIE_CADASTRO, emitida.token(), emitida.validade()))
                    .body(Map.of("emailVerificado", true));
        }
        sessoes.revogar(tokenAnterior);
        return resposta(HttpStatus.OK).header(HttpHeaders.SET_COOKIE,
                cookie(COOKIE, emitida.token(), emitida.validade())).body(emitida.conta());
    }

    @GetMapping("/sessao")
    public ResponseEntity<?> sessao(@CookieValue(name = COOKIE, required = false) String token) {
        var conta = sessoes.recuperar(token);
        return conta.isPresent() ? resposta(HttpStatus.OK).body(conta.get())
                : resposta(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping(value = "/sair", headers = CABECALHO + "=1")
    public ResponseEntity<Void> sair(@CookieValue(name = COOKIE, required = false) String token) {
        sessoes.revogar(token);
        return resposta(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookie(COOKIE, "", Duration.ZERO)).build();
    }

    private static ResponseEntity.BodyBuilder resposta(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    }

    private String cookie(String nome, String token, Duration validade) {
        return ResponseCookie.from(nome, token).path("/").httpOnly(true).secure(cookieSeguro)
                .sameSite("Strict").maxAge(validade).build().toString();
    }

    private synchronized boolean limitar(String endereco) {
        long minuto = Instant.now().getEpochSecond() / 60;
        limites.values().removeIf(limite -> limite.minuto() != minuto);
        Limite limite = limites.get(endereco);
        if (limite == null && limites.size() >= 1024
                || limite != null && limite.total() >= REQUISICOES_POR_MINUTO) {
            return true;
        }
        limites.put(endereco, new Limite(minuto, limite == null ? 1 : limite.total() + 1));
        return false;
    }
}
