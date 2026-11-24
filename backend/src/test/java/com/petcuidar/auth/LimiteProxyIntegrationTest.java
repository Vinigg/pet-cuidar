package com.petcuidar.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=127[.]0[.]0[.]1"
})
class LimiteProxyIntegrationTest {
    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry propriedades) {
        String url = "jdbc:sqlite:target/limite-proxy-" + UUID.randomUUID() + ".db";
        propriedades.add("spring.datasource.url", () -> url);
    }

    @Autowired TestRestTemplate http;
    @LocalServerPort int porta;

    @Test
    void limiteDistingueClientesDoMesmoProxyConfiavel() {
        for (int tentativa = 0; tentativa < 30; tentativa++) {
            assertEquals(202, solicitar("203.0.113.1"));
        }
        assertEquals(429, solicitar("203.0.113.1"));
        assertEquals(202, solicitar("203.0.113.2"));
        System.out.println("PASS C25 clientes do mesmo proxy têm cotas distintas");
    }

    private int solicitar(String ipOriginal) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pet-Cuidar", "1");
        headers.set("X-Pet-Cuidar-Client-IP", ipOriginal);
        var corpo = new HttpEntity<>("""
                {"email":"desconhecido@example.invalid","finalidade":"LOGIN"}
                """, headers);
        return http.postForEntity("http://127.0.0.1:" + porta + "/auth/codigo", corpo, String.class)
                .getStatusCode().value();
    }
}
