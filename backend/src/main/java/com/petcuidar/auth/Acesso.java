package com.petcuidar.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

public record Acesso(Conta conta, JdbcTemplate banco) {
    public static final String ATRIBUTO = "acessoAutenticado";

    public boolean eh(Perfil perfil) {
        return conta.perfil() == perfil;
    }

    public void verificarPet(Long petId) {
        if (eh(Perfil.TUTOR)) {
            exigir(vinculado("SELECT 1 FROM pets WHERE id = ? AND tutor_id = ?", petId, conta.tutorId()));
        }
        if (eh(Perfil.VETERINARIO)) {
            exigir(vinculado("SELECT 1 FROM consultas WHERE pet_id = ? AND veterinario_id = ?",
                    petId, conta.veterinarioId()));
        }
    }

    public void verificarConsulta(Long id) {
        if (eh(Perfil.TUTOR)) {
            exigir(vinculado("""
                    SELECT 1 FROM consultas c JOIN pets p ON p.id = c.pet_id
                    WHERE c.id = ? AND p.tutor_id = ?
                    """, id, conta.tutorId()));
        }
        if (eh(Perfil.VETERINARIO)) {
            exigir(vinculado("SELECT 1 FROM consultas WHERE id = ? AND veterinario_id = ?",
                    id, conta.veterinarioId()));
        }
    }

    public void verificarAtendimento(Long id) {
        exigir(vinculado("""
                SELECT 1 FROM historico_atendimentos h JOIN consultas c ON c.id = h.consulta_id
                WHERE h.id = ? AND c.veterinario_id = ?
                """, id, conta.veterinarioId()));
    }

    private boolean vinculado(String sql, Object... args) {
        return Boolean.TRUE.equals(banco.queryForObject("SELECT EXISTS (" + sql + ")", Boolean.class, args));
    }

    public static void exigir(boolean autorizado) {
        if (!autorizado) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
    }
}
