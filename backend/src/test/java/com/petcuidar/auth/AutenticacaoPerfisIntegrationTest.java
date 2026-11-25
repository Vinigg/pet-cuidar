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
    @Autowired com.petcuidar.repository.ConsultaRepository consultasAgenda;
    @Autowired com.petcuidar.repository.PetRepository petsAgenda;
    @Autowired com.petcuidar.repository.VeterinarioRepository veterinariosAgenda;
    @Autowired com.petcuidar.repository.BloqueioAgendaVeterinarioRepository bloqueiosAgenda;

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
        mvc.perform(post("/equipe/colaboradores").cookie(colaborador).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("""
                                {"nome":"Conta indevida","email":"indevido@example.invalid"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(post("/equipe/colaboradores").cookie(admin).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("""
                                {"nome":"Conta permitida","email":"permitido@example.invalid"}
                                """))
                .andExpect(status().isCreated());
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
        mvc.perform(get("/pets/meus").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pet));
        mvc.perform(get("/consultas/minhas").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(consulta));
        mvc.perform(patch("/consultas/{id}/status", outraConsulta).cookie(cookie)
                        .header("X-Pet-Cuidar", "1").contentType("application/json")
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/consultas/{id}/reagendamento", outraConsulta).cookie(cookie)
                        .header("X-Pet-Cuidar", "1").contentType("application/json")
                        .content("{\"dataHora\":\"2030-01-01T12:00:00\"}"))
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
        org.junit.jupiter.api.Assertions.assertEquals("AGENDADA", banco.queryForObject(
                "SELECT status FROM consultas WHERE id = ?", String.class, outraConsulta));
        org.junit.jupiter.api.Assertions.assertEquals("2024-01-15 10:00:00.000", banco.queryForObject(
                "SELECT data_hora FROM consultas WHERE id = ?", String.class, outraConsulta));
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
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String atendimentoProprio = mvc.perform(post("/atendimentos").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(atendimento(pet, consulta, veterinario)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.veterinarioId").value(veterinario))
                .andReturn().getResponse().getContentAsString();
        long idProprio = mapper.readTree(atendimentoProprio).get("id").longValue();
        mvc.perform(patch("/atendimentos").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("""
                                {"atendimentoId":%d,"veterinarioId":%d,"descricaoClinica":"Complemento da consulta."}
                                """.formatted(idProprio, veterinario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descricaoClinica").value("Complemento da consulta."));
        Cookie sessaoDoOutro = contaDeTeste("VETERINARIO", null, outro);
        long consultaExternaDoMesmoPet = novaConsulta(pet, outro);
        String atendimentoExterno = mvc.perform(post("/atendimentos").cookie(sessaoDoOutro)
                        .header("X-Pet-Cuidar", "1").contentType("application/json")
                        .content(atendimento(pet, consultaExternaDoMesmoPet, outro)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idExterno = mapper.readTree(atendimentoExterno).get("id").longValue();
        mvc.perform(patch("/atendimentos").cookie(cookie).header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content("""
                                {"atendimentoId":%d,"veterinarioId":%d,"descricaoClinica":"Tentativa indevida."}
                                """.formatted(idExterno, veterinario)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/atendimentos/{id}", idExterno).cookie(sessaoDoOutro)).andExpect(status().isOk())
                .andExpect(jsonPath("$.descricaoClinica").value("Observações clínicas."));
        System.out.println("PASS C14 Veterinário só manipula atendimentos vinculados");
    }

    @Test
    void minhaAgendaMostraGradeReservasEBloqueiosSomenteDoVeterinarioAutenticado() throws Exception {
        long vet = novoVeterinario(), outroVet = novoVeterinario();
        long pet = novoPet(novoTutor(), "Paciente da agenda");
        long consulta = consultaDaAgenda(pet, vet);
        consultaDaAgenda(novoPet(novoTutor(), "Paciente de outro profissional"), outroVet);
        banco.update("""
                INSERT INTO horarios_atendimento_veterinario (veterinario_id, dia_semana, hora_inicio, hora_fim)
                VALUES (?, 'MONDAY', '10:00', '12:00')
                """, vet);
        bloqueiosAgenda.saveAndFlush(new com.petcuidar.model.BloqueioAgendaVeterinario(
                veterinariosAgenda.findById(vet).orElseThrow(),
                java.time.LocalDateTime.parse("2024-01-15T11:00:00"),
                java.time.LocalDateTime.parse("2024-01-15T11:30:00"), "Intervalo"));
        Cookie cookie = contaDeTeste("VETERINARIO", null, vet);
        mvc.perform(get("/veterinarios/minha-agenda").cookie(cookie)
                        .param("data", "2024-01-15").param("veterinarioId", String.valueOf(outroVet)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2024-01-15"))
                .andExpect(jsonPath("$.consultas.length()").value(1))
                .andExpect(jsonPath("$.consultas[0].id").value(consulta))
                .andExpect(jsonPath("$.consultas[0].veterinarioId").value(vet))
                .andExpect(jsonPath("$.horarios.length()").value(4))
                .andExpect(jsonPath("$.horarios[0].horario").value("2024-01-15T10:00:00"))
                .andExpect(jsonPath("$.horarios[0].disponivel").value(false))
                .andExpect(jsonPath("$.horarios[1].disponivel").value(true))
                .andExpect(jsonPath("$.horarios[2].disponivel").value(false))
                .andExpect(jsonPath("$.horarios[3].disponivel").value(true));
        banco.update("UPDATE consultas SET status = 'CANCELADA' WHERE id = ?", consulta);
        mvc.perform(get("/veterinarios/minha-agenda").cookie(cookie).param("data", "2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].disponivel").value(true))
                .andExpect(jsonPath("$.consultas[0].status").value("CANCELADA"));
        mvc.perform(get("/veterinarios/minha-agenda").cookie(cookie).param("data", "2024-01-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(0))
                .andExpect(jsonPath("$.consultas.length()").value(0));
        mvc.perform(get("/veterinarios/minha-agenda").cookie(cookie).param("data", "invalida"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/veterinarios/minha-agenda").param("data", "2024-01-15"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/veterinarios/minha-agenda").cookie(contaDeTeste("ADMIN", null, null))
                        .param("data", "2024-01-15"))
                .andExpect(status().isForbidden());
    }

    private long consultaDaAgenda(long pet, long veterinario) {
        var consulta = new com.petcuidar.model.Consulta();
        consulta.setPet(petsAgenda.findById(pet).orElseThrow());
        consulta.setVeterinario(veterinariosAgenda.findById(veterinario).orElseThrow());
        consulta.setDataHora(java.time.LocalDateTime.parse("2024-01-15T10:00:00"));
        return consultasAgenda.saveAndFlush(consulta).getId();
    }

    @Test
    void cadastrosEEstadoDeLembreteSaoLeiturasRestritasSemDisparo() throws Exception {
        long tutor = novoTutor();
        long pet = novoPet(tutor, "Paciente da ficha");
        long veterinario = novoVeterinario();
        long consulta = novaConsulta(pet, veterinario);
        banco.update("UPDATE tutores SET telefone = ?, cpf = ? WHERE id = ?", "81999991234", "12345678909", tutor);
        Cookie colaborador = contaDeTeste("COLABORADOR", null, null);
        Cookie contaTutor = contaDeTeste("TUTOR", tutor, null);
        Cookie vet = contaDeTeste("VETERINARIO", null, veterinario);
        Cookie admin = contaDeTeste("ADMIN", null, null);
        mvc.perform(get("/tutores")).andExpect(status().isUnauthorized());
        for (Cookie perfil : new Cookie[]{contaTutor, vet, admin}) {
            mvc.perform(get("/tutores").cookie(perfil)).andExpect(status().isForbidden());
            mvc.perform(get("/consultas/{id}/lembrete", consulta).cookie(perfil))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/tutores").cookie(colaborador)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + tutor + ")].pets[0].nome")
                        .value(org.hamcrest.Matchers.hasItem("Paciente da ficha")))
                .andExpect(jsonPath("$[*].cpf").doesNotExist());
        org.mockito.Mockito.clearInvocations(remetente);
        Integer quantidade = banco.queryForObject("SELECT count(*) FROM lembretes", Integer.class);
        mvc.perform(get("/consultas/{id}/lembrete", consulta).cookie(colaborador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NAO_GERADO"))
                .andExpect(jsonPath("$.textoEnviado").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(quantidade,
                banco.queryForObject("SELECT count(*) FROM lembretes", Integer.class));
        org.mockito.Mockito.verifyNoInteractions(remetente);
        banco.update("""
                INSERT INTO lembretes (consulta_id, texto_validado, status_envio, usou_fallback, tentativas)
                VALUES (?, 'Mensagem realmente persistida', 'ENVIADO', 1, 1)
                """, consulta);
        mvc.perform(get("/consultas/{id}/lembrete", consulta).cookie(colaborador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENVIADO"))
                .andExpect(jsonPath("$.textoEnviado").value("Mensagem realmente persistida"))
                .andExpect(jsonPath("$.usouFallback").value(true));
        mvc.perform(get("/consultas/999999999/lembrete").cookie(colaborador))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(remetente);
    }

    @Test
    void identificaTutoresHomonimosSemAmpliarOsPacientesDoVeterinario() throws Exception {
        long tutor = novoTutor(), outroTutor = novoTutor();
        long pet = novoPet(tutor, "Luna"), outroPet = novoPet(outroTutor, "Luna");
        long segundoPet = novoPet(tutor, "Luna");
        long veterinario = novoVeterinario(), outroVeterinario = novoVeterinario();
        novaConsulta(pet, veterinario);
        novaConsulta(segundoPet, veterinario);
        novaConsulta(outroPet, outroVeterinario);
        Cookie colaborador = contaDeTeste("COLABORADOR", null, null);
        Cookie vet = contaDeTeste("VETERINARIO", null, veterinario);
        Cookie titular = contaDeTeste("TUTOR", tutor, null);

        mvc.perform(get("/pets").cookie(colaborador).param("tutorId", String.valueOf(outroTutor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(outroPet))
                .andExpect(jsonPath("$[0].tutorId").value(outroTutor))
                .andExpect(jsonPath("$[0].tutorNome").value("Tutor"));
        mvc.perform(get("/pets").cookie(vet).param("tutorId", String.valueOf(outroTutor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tutorId").value(tutor))
                .andExpect(jsonPath("$[1].tutorId").value(tutor))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem((int) outroPet))));
        mvc.perform(get("/pets").cookie(titular).param("tutorId", String.valueOf(outroTutor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tutorId").value(tutor))
                .andExpect(jsonPath("$[1].tutorId").value(tutor));
    }

    private Cookie contaDeTeste(String perfil, Long tutor, Long veterinario) {
        String email = tutor == null ? UUID.randomUUID() + "@example.com"
                : banco.queryForObject("SELECT email_normalizado FROM tutores WHERE id = ?", String.class, tutor);
        long id = banco.queryForObject("""
                INSERT INTO contas (email, nome, perfil, tutor_id, veterinario_id)
                VALUES (?, 'Conta de teste', ?, ?, ?) RETURNING id
                """, Long.class, email, perfil, tutor, veterinario);
        return SessaoTeste.credencial(banco, id);
    }

    private long novoTutor() {
        String email = UUID.randomUUID() + "@example.com";
        return banco.queryForObject("""
                INSERT INTO tutores (nome, email, email_normalizado) VALUES ('Tutor', ?, ?) RETURNING id
                """, Long.class, email, email);
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

    @Test
    void cadastroVerificaEmailAntesDeCriarTutorESessao() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String outroEmail = UUID.randomUUID() + "@example.com";
        String pedido = """
                {"nome":"Nova tutora","email":"%s","perfil":"ADMIN",
                 "pets":[{"nome":"Novo cão","especie":"Cachorro"}]}
                """.formatted(outroEmail);

        mvc.perform(post("/auth/cadastro").header("X-Pet-Cuidar", "1")
                        .contentType("application/json").content(pedido))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                banco.queryForObject("SELECT count(*) FROM tutores WHERE email IN (?, ?)", Integer.class,
                        email, outroEmail));
        System.out.println("PASS C19 autocadastro sem prova não grava dados");

        String codigo = solicitar(email, "CADASTRO");
        var verificado = mvc.perform(post("/auth/verificar").header("X-Pet-Cuidar", "1")
                        .contentType("application/json")
                        .content(solicitacao(email, "CADASTRO").replace("}", ",\"codigo\":\"" + codigo + "\"}")))
                .andExpect(status().isOk()).andExpect(cookie().exists("pc_cadastro"))
                .andExpect(cookie().doesNotExist("pc_sessao")).andReturn().getResponse();
        Cookie prova = verificado.getCookie("pc_cadastro");
        mvc.perform(get("/pets/meus").cookie(new Cookie("pc_sessao", prova.getValue())))
                .andExpect(status().isUnauthorized());
        var resultado = mvc.perform(post("/auth/cadastro").header("X-Pet-Cuidar", "1").cookie(prova)
                        .contentType("application/json").content(pedido))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.perfil").value("TUTOR"))
                .andExpect(jsonPath("$.email").value(email)).andExpect(cookie().maxAge("pc_cadastro", 0))
                .andReturn().getResponse();
        mvc.perform(get("/pets/meus").cookie(resultado.getCookie("pc_sessao")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nome").value("Novo cão"));
        mvc.perform(post("/auth/cadastro").header("X-Pet-Cuidar", "1").cookie(prova)
                        .contentType("application/json").content(pedido))
                .andExpect(status().isUnauthorized());
        System.out.println("PASS C20 autocadastro usa e-mail verificado e perfil Tutor");
    }

    @Test
    void emailAmbiguoNaoPodeEntrarNemSerRecadastrado() throws Exception {
        String email = UUID.randomUUID() + "@éxemplo.invalid";
        banco.update("""
                INSERT INTO tutores (nome, email, email_normalizado) VALUES
                    ('Primeiro', ?, ?), ('Segundo', ?, ?)
                """, email, email, email.toUpperCase(java.util.Locale.ROOT), email);
        for (String finalidade : new String[]{"LOGIN", "CADASTRO"}) {
            mvc.perform(post("/auth/codigo").header("X-Pet-Cuidar", "1").contentType("application/json")
                            .content(solicitacao(email, finalidade))).andExpect(status().isAccepted());
            mvc.perform(post("/auth/verificar").header("X-Pet-Cuidar", "1").contentType("application/json")
                            .content(solicitacao(email, finalidade).replace("}", ",\"codigo\":\"11111111\"}")))
                    .andExpect(status().isUnauthorized());
        }
        org.mockito.Mockito.verify(remetente, org.mockito.Mockito.after(400).never()).enviar(
                org.mockito.ArgumentMatchers.eq(email), anyString(), anyString(), anyString());
        System.out.println("PASS C27 e-mail ambíguo não permite autenticação");
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
