package com.petcuidar.auth;

import com.petcuidar.service.email.EmailDeliveryException;
import com.petcuidar.service.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.petcuidar.auth.DesafioEmailService.Finalidade.LOGIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class DesafioEmailIntegrationTest {
    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry propriedades) {
        String url = "jdbc:sqlite:target/desafios-" + UUID.randomUUID() + ".db";
        propriedades.add("spring.datasource.url", () -> url);
    }

    @Autowired JdbcTemplate banco;
    @Autowired DesafioEmailService desafios;
    @MockBean EmailSender remetente;
    private final LinkedBlockingQueue<String> entregues = new LinkedBlockingQueue<>();
    private String email;

    @BeforeEach
    void preparar() throws Exception {
        email = UUID.randomUUID() + "@example.com";
        banco.update("INSERT INTO contas (email, nome, perfil) VALUES (?, 'Ana', 'COLABORADOR')", email);
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
        assertFalse(desafios.consumir(email, LOGIN, vencido));
        String anterior = solicitarCodigo();
        banco.update("UPDATE desafios_email SET criado_em = 0 WHERE email = ?", email);
        String atual = solicitarCodigo();
        assertNotEquals(anterior, atual);
        assertFalse(desafios.consumir(email, LOGIN, anterior));
        assertTrue(desafios.consumir(email, LOGIN, atual));
        assertFalse(desafios.consumir(email, LOGIN, atual));
        System.out.println("PASS C22 código incorreto expirado consumido ou substituído é rejeitado");
    }

    @Test
    void somenteUmaRequisicaoPodeConsumirMesmoCodigo() throws Exception {
        String codigo = solicitarCodigo();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> desafios.consumir(email, LOGIN, codigo));
            var segunda = executor.submit(() -> desafios.consumir(email, LOGIN, codigo));
            assertNotEquals(primeira.get(5, TimeUnit.SECONDS), segunda.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        System.out.println("PASS C23 somente um consumo concorrente tem sucesso");
    }

    @Test
    void limitaReenvioETentativas() throws Exception {
        String codigo = solicitarCodigo();
        desafios.solicitar(email, LOGIN);
        assertNull(entregues.poll(400, TimeUnit.MILLISECONDS));
        assertTrue(banco.queryForObject("SELECT expira_em > ? FROM desafios_email WHERE email = ?",
                Boolean.class, Instant.now().getEpochSecond(), email));
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            assertFalse(desafios.consumir(email, LOGIN, "invalido"));
        }
        assertFalse(desafios.consumir(email, LOGIN, codigo));
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
        assertFalse(desafios.consumir(email, LOGIN, codigo));
        System.out.println("PASS C26 falha de envio descarta o desafio");
    }

    private String solicitarCodigo() throws InterruptedException {
        desafios.solicitar(email, LOGIN);
        String codigo = entregues.poll(5, TimeUnit.SECONDS);
        assertNotNull(codigo);
        assertTrue(codigo.matches("[0-9]{8}"));
        return codigo;
    }
}
