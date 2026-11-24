package com.petcuidar.auth;

import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.dto.TutorResponseDTO;
import com.petcuidar.exception.CadastroConflitanteException;
import com.petcuidar.service.TutorService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CadastroTutorService {
    private final JdbcTemplate banco;
    private final SessaoService sessoes;
    private final TutorService tutores;
    private final ContasService contas;

    public CadastroTutorService(JdbcTemplate banco, SessaoService sessoes, TutorService tutores, ContasService contas) {
        this.banco = banco;
        this.sessoes = sessoes;
        this.tutores = tutores;
        this.contas = contas;
    }

    @Transactional
    public TutorResponseDTO cadastrarPeloColaborador(TutorCadastroDTO pedido) {
        String email = SessaoService.normalizarEmail(pedido.getEmail());
        banco.update("UPDATE contas SET email = email WHERE email = ?", email);
        return cadastrar(pedido, email);
    }

    @Transactional
    public SessaoService.Credencial concluir(TutorCadastroDTO pedido, String prova) {
        String email = sessoes.consumirProvaCadastro(prova)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Confirme seu e-mail antes de concluir o cadastro."));
        cadastrar(pedido, email);
        return sessoes.iniciar(sessoes.buscarConta(email).orElseThrow());
    }

    private TutorResponseDTO cadastrar(TutorCadastroDTO pedido, String email) {
        try {
            contas.exigirEmailLivre(email, null);
        } catch (CadastroConflitanteException ex) {
            throw new CadastroConflitanteException("E-mail ja cadastrado.");
        }
        pedido.setEmail(email);
        TutorResponseDTO tutor = tutores.cadastrar(pedido);
        banco.update("""
                INSERT INTO contas (email, nome, perfil, tutor_id) VALUES (?, ?, 'TUTOR', ?)
                """, tutor.email(), tutor.nome(), tutor.id());
        return tutor;
    }
}
