package com.petcuidar.auth;

import com.petcuidar.exception.CadastroConflitanteException;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.petcuidar.auth.SessaoService.normalizarEmail;

@Service
public class ContasService implements ApplicationRunner {
    public record Membro(long id, String nome, String email, String perfil, Long veterinarioId) {}

    private final JdbcTemplate banco;
    private final Validator validador;
    private final String adminEmail;

    public ContasService(JdbcTemplate banco, Validator validador,
                         @Value("${app.auth.admin-email:}") String adminEmail) {
        this.banco = banco;
        this.validador = validador;
        this.adminEmail = normalizarEmail(adminEmail);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isEmpty()) {
            return;
        }
        if (!validador.validate(new EquipeController.EmailConta(adminEmail)).isEmpty()) {
            throw new IllegalStateException("AUTH_ADMIN_EMAIL deve ser um endereço válido.");
        }
        if (existe("SELECT 1 FROM contas WHERE email = ? AND perfil = 'ADMIN'", adminEmail)) {
            return;
        }
        exigirEmailLivre(adminEmail, null);
        banco.update("INSERT INTO contas (email, nome, perfil) VALUES (?, 'Admin', 'ADMIN')", adminEmail);
    }

    @Transactional(readOnly = true)
    public List<Membro> listarEquipe() {
        return banco.query("""
                SELECT id, nome, email, perfil, veterinario_id FROM contas
                WHERE perfil IN ('VETERINARIO', 'COLABORADOR') ORDER BY lower(nome), id
                """, (rs, row) -> {
            long veterinarioId = rs.getLong("veterinario_id");
            Long vinculo = rs.wasNull() ? null : veterinarioId;
            return new Membro(rs.getLong("id"), rs.getString("nome"), rs.getString("email"),
                    rs.getString("perfil"), vinculo);
        });
    }

    @Transactional
    public Membro cadastrarColaborador(String nome, String email) {
        email = normalizarEmail(email);
        banco.update("UPDATE contas SET email = email WHERE email = ?", email);
        exigirEmailLivre(email, null);
        long id = banco.queryForObject("""
                INSERT INTO contas (email, nome, perfil) VALUES (?, ?, 'COLABORADOR') RETURNING id
                """, Long.class, email, nome.strip());
        return new Membro(id, nome.strip(), email, "COLABORADOR", null);
    }

    @Transactional
    public Membro vincularVeterinario(long veterinarioId, String email) {
        email = normalizarEmail(email);
        banco.update("UPDATE contas SET email = email WHERE veterinario_id = ?", veterinarioId);
        String nome = banco.query("SELECT nome FROM veterinarios WHERE id = ?", (rs, n) -> rs.getString("nome"),
                veterinarioId).stream().findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinário não encontrado."));
        exigirEmailLivre(email, veterinarioId);
        banco.update("""
                DELETE FROM credenciais_auth WHERE conta_id IN
                   (SELECT id FROM contas WHERE veterinario_id = ? AND email <> ?)
                """, veterinarioId, email);
        long id = banco.queryForObject("""
                INSERT INTO contas (email, nome, perfil, veterinario_id)
                VALUES (?, ?, 'VETERINARIO', ?)
                ON CONFLICT(veterinario_id) DO UPDATE SET email = excluded.email, nome = excluded.nome
                RETURNING id
                """, Long.class, email, nome, veterinarioId);
        return new Membro(id, nome, email, "VETERINARIO", veterinarioId);
    }

    public void exigirEmailLivre(String email, Long veterinarioId) {
        if (existe("""
                SELECT 1 FROM contas WHERE email = ? AND
                    (? IS NULL OR veterinario_id IS NULL OR veterinario_id <> ?)
                """, email, veterinarioId, veterinarioId)
                || existe("SELECT 1 FROM tutores WHERE email_normalizado = ?", email)) {
            throw new CadastroConflitanteException("Este e-mail já está vinculado a um cadastro.");
        }
    }

    private boolean existe(String sql, Object... parametros) {
        return Boolean.TRUE.equals(banco.queryForObject("SELECT EXISTS (" + sql + ")", Boolean.class, parametros));
    }
}
