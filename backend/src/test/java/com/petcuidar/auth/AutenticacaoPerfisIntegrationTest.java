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
@SpringBootTest(properties = "app.auth.admin-email=admin-inicial@example.invalid")
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

    @Test
    void exigeSessaoERejeitaPerfisIncompativeis() throws Exception {
        mvc.perform(get("/pets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/pets").cookie(new Cookie("pc_sessao", "credencial-invalida")))
                .andExpect(status().isUnauthorized());
        System.out.println("PASS C11 API retorna 401 para sessão ausente ou inválida");

        Cookie colaborador = autenticar(novoColaborador(), "COLABORADOR");
        Cookie admin = contaDeTeste("ADMIN", null, null);
        mvc.perform(get("/pets").cookie(colaborador)).andExpect(status().isOk());
        mvc.perform(get("/pets").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/pets/1/historico").cookie(colaborador)).andExpect(status().isForbidden());
        mvc.perform(post("/tutores").cookie(colaborador).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        System.out.println("PASS C12 API retorna 403 para perfil incorreto");
    }

    @Test
    void tutorNaoAcessaNemAlteraDadosDeOutroTutor() throws Exception {
        long tutor = novoTutor(), outroTutor = novoTutor();
        long pet = novoPet(tutor, "Meu pet"), outroPet = novoPet(outroTutor, "Outro pet");
        long veterinario = novoVeterinario();
        long consulta = novaConsulta(pet, veterinario), outraConsulta = novaConsulta(outroPet, veterinario);
        Cookie cookie = contaDeTeste("TUTOR", tutor, null);

        mvc.perform(get("/pets").param("tutorId", String.valueOf(outroTutor)).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pet));
        mvc.perform(get("/consultas/minhas").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(consulta));
        mvc.perform(patch("/consultas/{id}/status", outraConsulta).cookie(cookie)
                        .header("X-Pet-Cuidar", "1").contentType("application/json")
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/consultas").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("""
                                {"petId":%d,"veterinarioId":%d,"dataHora":"2030-01-01T12:00:00"}
                                """.formatted(outroPet, veterinario))).andExpect(status().isForbidden());
        mvc.perform(post("/pets").cookie(cookie).header("X-Pet-Cuidar", "1").contentType("application/json")
                        .content("{\"nome\":\"Novo pet\",\"tutorId\":" + outroTutor + "}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(tutor, banco.queryForObject(
                "SELECT tutor_id FROM pets WHERE nome = 'Novo pet'", Long.class));
        System.out.println("PASS C13 Tutor só manipula pets e consultas da própria conta");
    }

    @Test
    void veterinarioFicaRestritoAConsultasDoSeuCadastro() throws Exception {
        long tutor = novoTutor();
        long pet = novoPet(tutor, "Paciente"), outroPet = novoPet(tutor, "Outro paciente");
        long veterinario = novoVeterinario(), outro = novoVeterinario();
        long consulta = novaConsulta(pet, veterinario), outraConsulta = novaConsulta(outroPet, outro);
        Cookie cookie = contaDeTeste("VETERINARIO", null, veterinario);
        mvc.perform(get("/pets/{id}/historico", pet).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/pets/{id}/historico", outroPet).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/atendimentos").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(atendimento(outroPet, outraConsulta, veterinario)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/atendimentos").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(atendimento(pet, consulta, outro)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/atendimentos").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(atendimento(pet, consulta, veterinario)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.veterinarioId").value(veterinario));
        System.out.println("PASS C14 Veterinário só manipula atendimentos vinculados");
    }

    private Cookie contaDeTeste(String perfil, Long tutor, Long veterinario) {
        String email = UUID.randomUUID() + "@example.com";
        long id = banco.queryForObject("""
                INSERT INTO contas (email, nome, perfil, tutor_id, veterinario_id)
                VALUES (?, 'Conta de teste', ?, ?, ?) RETURNING id
                """, Long.class, email, perfil, tutor, veterinario);
        return SessaoTeste.credencial(banco, id);
    }

    private long novoTutor() {
        return banco.queryForObject("INSERT INTO tutores (nome, email) VALUES ('Tutor', ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com");
    }

    private long novoPet(long tutor, String nome) {
        return banco.queryForObject("INSERT INTO pets (nome, tutor_id) VALUES (?, ?) RETURNING id",
                Long.class, nome, tutor);
    }

    private long novoVeterinario() {
        return banco.queryForObject("INSERT INTO veterinarios (nome, ativo) VALUES ('Veterinário', 1) RETURNING id",
                Long.class);
    }

    private long novaConsulta(long pet, long veterinario) {
        return banco.queryForObject("""
                INSERT INTO consultas (pet_id, veterinario_id, data_hora, status)
                VALUES (?, ?, '2024-01-15 10:00:00.000', 'AGENDADA') RETURNING id
                """, Long.class, pet, veterinario);
    }

    private static String atendimento(long pet, long consulta, long veterinario) {
        return """
                {"petId":%d,"consultaId":%d,"veterinarioId":%d,"descricaoClinica":"Observações clínicas."}
                """.formatted(pet, consulta, veterinario);
    }

    @Test
    void adminConfiguradoProvisionaEquipeComPerfisFixos() throws Exception {
        Cookie admin = autenticar("admin-inicial@example.invalid", "ADMIN");
        System.out.println("PASS C15 admin inicial entra pelo e-mail configurado");
        long id = novoVeterinario();
        String veterinario = UUID.randomUUID() + "@example.com";
        mvc.perform(put("/equipe/veterinarios/{id}", id).cookie(admin).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("{\"email\":\"" + veterinario + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.veterinarioId").value(id));
        Cookie sessaoVet = autenticar(veterinario, "VETERINARIO");
        mvc.perform(get("/auth/sessao").cookie(sessaoVet)).andExpect(jsonPath("$.veterinarioId").value(id));
        System.out.println("PASS C16 admin provisiona veterinário vinculado");

        String colaborador = UUID.randomUUID() + "@example.com";
        String cadastro = "{\"nome\":\"Ana\",\"perfil\":\"ADMIN\",\"email\":\"" + colaborador + "\"}";
        mvc.perform(post("/equipe/colaboradores").cookie(admin).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(cadastro)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("COLABORADOR"));
        Cookie sessaoColaborador = autenticar(colaborador, "COLABORADOR");
        mvc.perform(get("/equipe").cookie(sessaoColaborador)).andExpect(status().isForbidden());
        System.out.println("PASS C17 admin provisiona colaborador");

        mvc.perform(put("/equipe/veterinarios/{id}", id).cookie(admin).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("{\"email\":\"" + colaborador + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/auth/sessao").cookie(sessaoVet)).andExpect(jsonPath("$.email").value(veterinario));
        mvc.perform(get("/auth/sessao").cookie(sessaoColaborador)).andExpect(jsonPath("$.perfil").value("COLABORADOR"));
        System.out.println("PASS C18 e-mail conflitante retorna 409");
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
