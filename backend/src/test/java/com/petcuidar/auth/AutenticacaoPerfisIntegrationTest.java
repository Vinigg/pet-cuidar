package com.petcuidar.auth;

import com.petcuidar.service.email.EmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest
class AutenticacaoPerfisIntegrationTest {
    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry propriedades) {
        String url = "jdbc:sqlite:target/autenticacao-" + UUID.randomUUID() + ".db";
        propriedades.add("spring.datasource.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate banco;
    @MockBean EmailSender remetente;

    @Test
    void loginUsaPerfilDoServidorESairRevogaSessao() throws Exception {
        String email = novoColaborador();
        Cookie cookie = autenticar(email, "COLABORADOR");
        mvc.perform(get("/auth/sessao").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.perfil").value("COLABORADOR"));
        System.out.println("PASS C2 código correto inicia sessão do perfil cadastrado");

        mvc.perform(post("/auth/sair").cookie(cookie).header("X-Pet-Cuidar", "1"))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("pc_sessao", 0));
        mvc.perform(get("/auth/sessao").cookie(cookie)).andExpect(status().isUnauthorized());
        System.out.println("PASS C3 sair invalida a sessão no servidor");
    }

    @Test
    void solicitaçãoDeEmailDesconhecidoTemMesmaRespostaPublica() throws Exception {
        String conhecida = novoColaborador();
        var resp = mvc.perform(post("/auth/codigo").header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(solicitacao(conhecida, "LOGIN")))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        mvc.perform(post("/auth/codigo").header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(solicitacao(UUID.randomUUID() + "@example.com", "LOGIN")))
                .andExpect(status().isAccepted()).andExpect(content().string(resp.getContentAsString()));
        System.out.println("PASS C24 e-mail desconhecido recebe resposta genérica");
    }

    private String novoColaborador() {
        String email = UUID.randomUUID() + "@example.com";
        banco.update("INSERT INTO contas (email, nome, perfil) VALUES (?, 'Ana', 'COLABORADOR')", email);
        return email;
    }

    private Cookie autenticar(String email, String perfil) throws Exception {
        String codigo = solicitar(email, "LOGIN");
        var resultado = mvc.perform(post("/auth/verificar").header("X-Pet-Cuidar", "1")
                .contentType("application/json")
                .content(solicitacao(email, "LOGIN").replace("}", ",\"codigo\":\"" + codigo + "\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value(perfil))
                .andExpect(cookie().httpOnly("pc_sessao", true))
                .andExpect(cookie().secure("pc_sessao", true)).andReturn();
        Cookie cookie = resultado.getResponse().getCookie("pc_sessao");
        assertNotNull(cookie);
        return cookie;
    }

    private String solicitar(String email, String finalidade) throws Exception {
        var entregues = new LinkedBlockingQueue<String>();
        doAnswer(chamada -> {
            entregues.add(chamada.<String>getArgument(2).split(" ")[3].substring(0, 8));
            return null;
        }).when(remetente).enviar(org.mockito.ArgumentMatchers.eq(email), anyString(), anyString(), anyString());
        mvc.perform(post("/auth/codigo").header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(solicitacao(email, finalidade)))
                .andExpect(status().isAccepted());
        String codigo = entregues.poll(5, TimeUnit.SECONDS);
        assertNotNull(codigo);
        return codigo;
    }

    private static String solicitacao(String email, String finalidade) {
        return "{\"email\":\"" + email + "\",\"finalidade\":\"" + finalidade + "\"}";
    }
}
