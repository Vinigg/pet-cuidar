package com.petcuidar.auth;

import com.petcuidar.service.email.EmailDeliveryException;
import com.petcuidar.service.email.EmailSender;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class DesafioEmailService {
    public enum Finalidade { LOGIN, CADASTRO }

    private static final int VALIDADE_SEGUNDOS = 600;
    private static final int INTERVALO_ENVIO_SEGUNDOS = 60;
    private static final int MAX_TENTATIVAS = 5;

    private final JdbcTemplate banco;
    private final EmailSender remetente;
    private final SecureRandom random = new SecureRandom();
    private final byte[] chave = random.generateSeed(32);
    private final ThreadPoolExecutor envios = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));

    public DesafioEmailService(JdbcTemplate banco, EmailSender remetente) {
        this.banco = banco;
        this.remetente = remetente;
    }

    public void solicitar(String email, Finalidade finalidade) {
        try {
            envios.execute(() -> prepararEnvio(email, finalidade));
        } catch (RejectedExecutionException ignored) {
            // A resposta pública é a mesma quando a fila está cheia.
        }
    }

    private void prepararEnvio(String email, Finalidade finalidade) {
        boolean existe = Boolean.TRUE.equals(banco.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM contas c WHERE c.email = ? AND
                    (c.veterinario_id IS NULL OR EXISTS (
                        SELECT 1 FROM veterinarios v WHERE v.id = c.veterinario_id AND v.ativo = 1
                    )))
                """, Boolean.class, email));
        if (finalidade == Finalidade.LOGIN ? !existe : existe || emailDeTutor(email)) {
            return;
        }

        String codigo = String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
        String digest = digest(email, finalidade, codigo);
        long agora = Instant.now().getEpochSecond();
        banco.update("DELETE FROM desafios_email WHERE expira_em <= ?", agora);

        int autorizado = banco.update("""
                INSERT INTO desafios_email (email, finalidade, digest, criado_em, expira_em)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(email, finalidade) DO UPDATE SET
                    digest = excluded.digest, criado_em = excluded.criado_em,
                    expira_em = excluded.expira_em, tentativas = 0
                WHERE desafios_email.criado_em <= ?
                """, email, finalidade.name(), digest, agora, agora + VALIDADE_SEGUNDOS,
                agora - INTERVALO_ENVIO_SEGUNDOS);
        if (autorizado == 0) {
            return;
        }

        try {
            remetente.enviar(email, "Seu código de acesso ao Pet Cuidar",
                    "Seu código é " + codigo + ". Ele expira em 10 minutos.",
                    "pet-cuidar-auth-" + UUID.randomUUID());
        } catch (EmailDeliveryException e) {
            banco.update("UPDATE desafios_email SET digest = NULL WHERE email = ? AND finalidade = ? AND digest = ?",
                    email, finalidade.name(), digest);
        }
    }

    private boolean emailDeTutor(String email) {
        return Boolean.TRUE.equals(banco.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tutores WHERE lower(trim(email)) = ?)", Boolean.class, email));
    }

    @Transactional
    public boolean consumir(String email, Finalidade finalidade, String codigo) {
        int atualizado = banco.update("""
                UPDATE desafios_email SET tentativas = tentativas + 1
                WHERE email = ? AND finalidade = ? AND digest IS NOT NULL AND expira_em > ? AND tentativas < ?
                """, email, finalidade.name(), Instant.now().getEpochSecond(), MAX_TENTATIVAS);
        if (atualizado == 0) {
            return false;
        }

        String esperado = banco.queryForObject(
                "SELECT digest FROM desafios_email WHERE email = ? AND finalidade = ?",
                String.class, email, finalidade.name());
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(esperado),
                HexFormat.of().parseHex(digest(email, finalidade, codigo)))) {
            return false;
        }
        banco.update("UPDATE desafios_email SET digest = NULL WHERE email = ? AND finalidade = ?",
                email, finalidade.name());
        return true;
    }

    private String digest(String email, Finalidade finalidade, String codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            String mensagem = finalidade + ":" + codigo + ":" + email;
            return HexFormat.of().formatHex(mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritmo de autenticação indisponível", e);
        }
    }

    @PreDestroy
    void encerrarEnvios() {
        envios.shutdownNow();
    }
}
