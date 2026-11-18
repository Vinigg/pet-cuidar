package com.petcuidar.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public final class SessaoTeste {
    private SessaoTeste() {}

    public static RequestPostProcessor assumir(JdbcTemplate banco, Perfil perfil) {
        return assumir(banco, perfil, null);
    }

    public static RequestPostProcessor assumir(JdbcTemplate banco, Perfil perfil, Long veterinarioId) {
        return request -> {
            String email = "fixture-" + perfil + "-" + veterinarioId + "@example.invalid";
            banco.update("""
                    INSERT INTO contas (email, nome, perfil, veterinario_id)
                    VALUES (?, 'Conta de teste', ?, ?) ON CONFLICT(email) DO NOTHING
                    """, email, perfil.name(), veterinarioId);
            long id = banco.queryForObject("SELECT id FROM contas WHERE email = ?", Long.class, email);
            request.setCookies(credencial(banco, id));
            request.addHeader("X-Pet-Cuidar", "1");
            return request;
        };
    }

    static Cookie credencial(JdbcTemplate banco, long id) {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(
                UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.US_ASCII));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            banco.update("INSERT INTO credenciais_auth (digest, conta_id, expira_em) VALUES (?, ?, ?)",
                    HexFormat.of().formatHex(digest), id, Instant.now().plusSeconds(600).getEpochSecond());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        return new Cookie(SessaoService.COOKIE, token);
    }
}
