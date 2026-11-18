package com.petcuidar.auth;

import com.petcuidar.service.email.EmailDeliveryException;
import com.petcuidar.service.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.petcuidar.auth.DesafioEmailService.Finalidade.LOGIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class DesafioEmailIntegrationTest {
    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry propriedades) {
        String url = "jdbc:sqlite:target/desafios-" + UUID.randomUUID() + ".db";
        propriedades.add("spring.datasource.url", () -> url);
    }

    @Autowired JdbcTemplate banco;
    @Autowired MockMvc mvc;
    @Autowired DesafioEmailService desafios;
    @MockBean EmailSender remetente;
    private final LinkedBlockingQueue<String> entregues = new LinkedBlockingQueue<>();
    private String email;

    @BeforeEach
    void preparar() throws Exception {
        email = UUID.randomUUID() + "@example.com";
        banco.update("INSERT INTO contas (email, nome, perfil) VALUES (?, 'Ana', 'COLABORADOR')", email);
        prepararRemetente();
    }

    private void prepararRemetente() throws Exception {
        doAnswer(chamada -> {
            assertEquals(email, chamada.getArgument(0));
            entregues.add(chamada.<String>getArgument(2).split(" ")[3].substring(0, 8));
            return null;
        }).when(remetente).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void enviaCodigoParaContaAtivaEPermiteUmConsumo() throws Exception {
        String codigo = solicitarCodigo();
        assertFalse(desafios.consumir(email, LOGIN, "invalido"));
        assertTrue(desafios.consumir(email, LOGIN, codigo));
        assertFalse(desafios.consumir(email, LOGIN, codigo));
        System.out.println("PASS C1 servidor envia o código da conta ativa");
    }

    @Test
    void rejeitaCodigoExpiradoOuSubstituido() throws Exception {
        String vencido = solicitarCodigo();
        banco.update("UPDATE desafios_email SET expira_em = 0 WHERE email = ?", email);
        rejeitar(vencido);
        String anterior = solicitarCodigo();
        banco.update("UPDATE desafios_email SET criado_em = 0 WHERE email = ?", email);
        String atual = solicitarCodigo();
        assertNotEquals(anterior, atual);
        rejeitar(anterior);
        rejeitar(atual.equals("12345678") ? "23456789" : "12345678");
        var resposta = verificar(atual);
        assertEquals(200, resposta.getStatus());
        assertNotNull(resposta.getCookie(SessaoService.COOKIE));
        rejeitar(atual);
        System.out.println("PASS C22 código incorreto expirado consumido ou substituído é rejeitado");
    }

    @Test
    void somenteUmaRequisicaoPodeConsumirMesmoCodigo() throws Exception {
        String codigo = solicitarCodigo();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> verificar(codigo));
            var segunda = executor.submit(() -> verificar(codigo));
            var respostas = List.of(primeira.get(5, TimeUnit.SECONDS), segunda.get(5, TimeUnit.SECONDS));
            assertEquals(List.of(200, 401), respostas.stream().map(MockHttpServletResponse::getStatus).sorted().toList());
            assertEquals(1, respostas.stream().filter(resp -> resp.getCookie(SessaoService.COOKIE) != null).count());
            assertEquals(1, banco.queryForObject("""
                    SELECT count(*) FROM credenciais_auth a JOIN contas c ON c.id = a.conta_id WHERE c.email = ?
                    """, Integer.class, email));
        } finally {
            executor.shutdownNow();
        }
        System.out.println("PASS C23 somente um consumo concorrente tem sucesso");
    }

    @Test
    void limitaReenvioETentativas() throws Exception {
        String codigo = solicitarCodigo();
        banco.update("UPDATE desafios_email SET criado_em = criado_em - 30, expira_em = expira_em - 30 WHERE email = ?", email);
        Long expiraEm = banco.queryForObject("SELECT expira_em FROM desafios_email WHERE email = ?", Long.class, email);
        desafios.solicitar(email, LOGIN);
        assertNull(entregues.poll(400, TimeUnit.MILLISECONDS));
        assertEquals(expiraEm, banco.queryForObject("SELECT expira_em FROM desafios_email WHERE email = ?", Long.class, email));
        String incorreto = codigo.equals("12345678") ? "23456789" : "12345678";
        for (int tentativa = 0; tentativa < 5; tentativa++) rejeitar(incorreto);
        rejeitar(codigo);
        assertEquals(expiraEm, banco.queryForObject("SELECT expira_em FROM desafios_email WHERE email = ?", Long.class, email));

        String pedido = "{\"email\":\"desconhecido@example.invalid\",\"finalidade\":\"LOGIN\"}";
        for (int tentativa = 0; tentativa <= 30; tentativa++) {
            mvc.perform(post("/auth/codigo").header("X-Pet-Cuidar", "1").contentType("application/json").content(pedido)
                            .with(request -> { request.setRemoteAddr("203.0.113.4"); return request; }))
                    .andExpect(status().is(tentativa < 30 ? 202 : 429));
        }
        System.out.println("PASS C25 limites de envio e verificação são aplicados");
    }

    @Test
    void falhaDeEnvioInvalidaDesafio() throws Exception {
        doAnswer(chamada -> {
            entregues.add(chamada.<String>getArgument(2).split(" ")[3].substring(0, 8));
            throw new EmailDeliveryException("falha simulada", true);
        }).when(remetente).enviar(anyString(), anyString(), anyString(), anyString());
        String codigo = solicitarCodigo();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!banco.queryForObject("SELECT digest IS NULL FROM desafios_email WHERE email = ?", Boolean.class, email)
                && System.nanoTime() < limite) {
            Thread.sleep(10);
        }
        rejeitar(codigo);

        desafios.solicitar(email, LOGIN);
        assertNull(entregues.poll(400, TimeUnit.MILLISECONDS));
        banco.update("UPDATE desafios_email SET criado_em = 0 WHERE email = ?", email);
        prepararRemetente();
        var resposta = verificar(solicitarCodigo());
        assertEquals(200, resposta.getStatus());
        assertNotNull(resposta.getCookie(SessaoService.COOKIE));
        System.out.println("PASS C26 falha de envio descarta o desafio");
    }

    private MockHttpServletResponse verificar(String codigo) throws Exception {
        return mvc.perform(post("/auth/verificar").header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("""
                                {"email":"%s","finalidade":"LOGIN","codigo":"%s"}
                                """.formatted(email, codigo)))
                .andReturn().getResponse();
    }

    private void rejeitar(String codigo) throws Exception {
        var resposta = verificar(codigo);
        assertEquals(401, resposta.getStatus());
        assertNull(resposta.getCookie(SessaoService.COOKIE));
    }

    private String solicitarCodigo() throws InterruptedException {
        desafios.solicitar(email, LOGIN);
        String codigo = entregues.poll(5, TimeUnit.SECONDS);
        assertNotNull(codigo);
        assertTrue(codigo.matches("[0-9]{8}"));
        return codigo;
    }
}
