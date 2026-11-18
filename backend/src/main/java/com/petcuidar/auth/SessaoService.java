package com.petcuidar.auth;

import com.petcuidar.model.EmailCanonico;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static com.petcuidar.auth.DesafioEmailService.Finalidade;

@Service
public class SessaoService {
    public static final String COOKIE = "pc_sessao";
    public static final String COOKIE_CADASTRO = "pc_cadastro";
    static final Duration VALIDADE_SESSAO = Duration.ofHours(8);
    static final Duration VALIDADE_CADASTRO = Duration.ofMinutes(10);

    static final String CONTA_ATIVA = """
            (c.veterinario_id IS NULL OR EXISTS (
                SELECT 1 FROM veterinarios v WHERE v.id = c.veterinario_id AND v.ativo = 1
            ))
            AND (c.tutor_id IS NULL OR EXISTS (
                SELECT 1 FROM tutores t WHERE t.id = c.tutor_id AND t.email_normalizado = c.email
            ))
            AND (SELECT count(*) FROM tutores t WHERE t.email_normalizado = c.email)
                = CASE WHEN c.perfil = 'TUTOR' THEN 1 ELSE 0 END
            """;

    public record Credencial(String token, Duration validade, Conta conta) {}

    private final JdbcTemplate banco;
    private final DesafioEmailService desafios;
    private final SecureRandom random = new SecureRandom();

    public SessaoService(JdbcTemplate banco, DesafioEmailService desafios) {
        this.banco = banco;
        this.desafios = desafios;
    }

    public static String normalizarEmail(String email) {
        return EmailCanonico.normalizar(email);
    }

    @Transactional
    public Optional<Credencial> verificar(String email, Finalidade finalidade, String codigo) {
        if (!desafios.consumir(email, finalidade, codigo)) {
            return Optional.empty();
        }
        if (finalidade == Finalidade.CADASTRO) {
            return Optional.of(emitir(null, email, VALIDADE_CADASTRO));
        }
        return buscarConta(email).map(this::iniciar);
    }

    Optional<Conta> buscarConta(String email) {
        return banco.query("SELECT c.* FROM contas c WHERE c.email = ? AND " + CONTA_ATIVA,
                SessaoService::mapear, email).stream().findFirst();
    }

    Credencial iniciar(Conta conta) {
        return emitir(conta, null, VALIDADE_SESSAO);
    }

    private Credencial emitir(Conta conta, String email, Duration validade) {
        byte[] aleatorio = new byte[32];
        random.nextBytes(aleatorio);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        long agora = Instant.now().getEpochSecond();
        banco.update("DELETE FROM credenciais_auth WHERE expira_em <= ?", agora);
        banco.update("""
                INSERT INTO credenciais_auth (digest, conta_id, email, expira_em) VALUES (?, ?, ?, ?)
                """, digest(token), conta == null ? null : conta.id(), email, agora + validade.toSeconds());
        return new Credencial(token, validade, conta);
    }

    public Optional<Conta> recuperar(String token) {
        if (!tokenValido(token)) {
            return Optional.empty();
        }
        return banco.query("""
                SELECT c.* FROM credenciais_auth a JOIN contas c ON c.id = a.conta_id
                WHERE a.digest = ? AND a.expira_em > ? AND
                """ + CONTA_ATIVA, SessaoService::mapear, digest(token), Instant.now().getEpochSecond())
                .stream().findFirst();
    }

    public void revogar(String token) {
        if (tokenValido(token)) {
            banco.update("DELETE FROM credenciais_auth WHERE digest = ? AND conta_id IS NOT NULL", digest(token));
        }
    }

    Optional<String> consumirProvaCadastro(String token) {
        if (!tokenValido(token)) {
            return Optional.empty();
        }
        return banco.query("""
                DELETE FROM credenciais_auth WHERE digest = ? AND email IS NOT NULL AND expira_em > ?
                RETURNING email
                """, (rs, n) -> rs.getString("email"), digest(token), Instant.now().getEpochSecond())
                .stream().findFirst();
    }

    private static Conta mapear(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Conta(rs.getLong("id"), rs.getString("email"), rs.getString("nome"),
                Perfil.valueOf(rs.getString("perfil")), idOpcional(rs, "tutor_id"), idOpcional(rs, "veterinario_id"));
    }

    private static Long idOpcional(java.sql.ResultSet rs, String coluna) throws java.sql.SQLException {
        long id = rs.getLong(coluna);
        return rs.wasNull() ? null : id;
    }

    private static boolean tokenValido(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
