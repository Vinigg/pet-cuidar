package com.petcuidar.controller;

import com.petcuidar.model.*;
import com.petcuidar.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /consultas/{id} ponta a ponta: HTTP -> service -> repositorio -> SQLite isolado.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsultaDetalheIntegrationTest {
    private static final String DB = "jdbc:sqlite:target/detalhe-" + UUID.randomUUID() + ".db";
    private static final LocalDate DIA = LocalDate.now().plusDays(30);

    @DynamicPropertySource
    static void bancoIsolado(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB);
    }

    @Autowired MockMvc mvc;
    @Autowired ConsultaRepository consultas;
    @Autowired PetRepository pets;
    @Autowired TutorRepository tutores;
    @Autowired VeterinarioRepository veterinarios;
    @Autowired LembreteRepository lembretes;
    @Autowired PredicaoNoShowRepository predicoes;
    @Autowired EntityManagerFactory emf;

    private Tutor tutor;
    private Pet pet;
    private Veterinario vet;

    @BeforeEach
    void preparar() {
        lembretes.deleteAll();
        predicoes.deleteAll();
        consultas.deleteAll();
        pets.deleteAll();
        tutores.deleteAll();

        Tutor t = new Tutor();
        t.setNome("Ana Souza");
        t.setEmail(UUID.randomUUID() + "@example.com");
        t.setTelefone("11999998888");
        t.setCpf(UUID.randomUUID().toString().substring(0, 11));
        tutor = tutores.save(t);

        Pet p = new Pet();
        p.setNome("Rex");
        p.setEspecie("Cachorro");
        p.setIdade(4);
        p.setAlergiasCriticas("Dipirona");
        p.setTutor(tutor);
        pet = pets.save(p);

        vet = veterinarios.findAll().get(0);
    }

    private Consulta consulta(LocalDateTime dataHora) {
        Consulta c = new Consulta();
        c.setPet(pet);
        c.setVeterinario(vet);
        c.setDataHora(dataHora);
        return consultas.save(c);
    }

    @Test
    void consultaExistenteRetornaDetalheComRelacionamentos() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 30));

        mvc.perform(get("/consultas/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.id").value(c.getId()))
                .andExpect(jsonPath("$.dataHora").value(DIA.atTime(10, 30) + ":00"))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.pet.id").value(pet.getId()))
                .andExpect(jsonPath("$.pet.nome").value("Rex"))
                .andExpect(jsonPath("$.pet.especie").value("Cachorro"))
                .andExpect(jsonPath("$.pet.idade").value(4))
                .andExpect(jsonPath("$.pet.alergiasCriticas").value("Dipirona"))
                .andExpect(jsonPath("$.tutor.id").value(tutor.getId()))
                .andExpect(jsonPath("$.tutor.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.tutor.email").value(tutor.getEmail()))
                .andExpect(jsonPath("$.tutor.telefone").value("11999998888"))
                .andExpect(jsonPath("$.veterinario.id").value(vet.getId()))
                .andExpect(jsonPath("$.veterinario.nome").value(vet.getNome()))
                .andExpect(jsonPath("$.veterinario.especialidade").value(vet.getEspecialidade()));
    }

    @Test
    void naoExpoeDadosSensiveisNemInternos() throws Exception {
        Consulta c = consulta(DIA.atTime(11, 0));
        mvc.perform(get("/consultas/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutor.cpf").doesNotExist())
                .andExpect(jsonPath("$.tutor.pets").doesNotExist())
                .andExpect(jsonPath("$.pet.tutor").doesNotExist())
                .andExpect(jsonPath("$.veterinario.versao").doesNotExist());
    }

    @Test
    void camposOpcionaisAusentesVoltamNulos() throws Exception {
        pet.setEspecie(null);
        pet.setIdade(null);
        pet.setAlergiasCriticas(null);
        pets.save(pet);
        Consulta c = consulta(DIA.atTime(11, 30));
        mvc.perform(get("/consultas/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pet.nome").value("Rex"))
                .andExpect(jsonPath("$.pet.especie").isEmpty())
                .andExpect(jsonPath("$.pet.idade").isEmpty())
                .andExpect(jsonPath("$.pet.alergiasCriticas").isEmpty());
    }

    @Test
    void consultaInexistenteRetorna404NoFormatoPadrao() throws Exception {
        mvc.perform(get("/consultas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Consulta nao encontrada: id 999999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void identificadorNaoNumericoRetorna400() throws Exception {
        mvc.perform(get("/consultas/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void identificadorZeroOuNegativoRetorna400() throws Exception {
        mvc.perform(get("/consultas/0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Identificador de consulta invalido."));
        mvc.perform(get("/consultas/-5")).andExpect(status().isBadRequest());
    }

    @Test
    void visualizacaoNaoAlteraDados() throws Exception {
        Consulta c = consulta(DIA.atTime(14, 0));
        mvc.perform(get("/consultas/" + c.getId())).andExpect(status().isOk());
        mvc.perform(get("/consultas/" + c.getId())).andExpect(status().isOk());

        Consulta recarregada = consultas.findById(c.getId()).orElseThrow();
        assertEquals(StatusConsulta.AGENDADA, recarregada.getStatus());
        assertEquals(DIA.atTime(14, 0), recarregada.getDataHora());
        assertTrue(lembretes.findByConsultaId(c.getId()).isEmpty());
        assertTrue(predicoes.findByConsultaId(c.getId()).isEmpty());
    }

    @Test
    void detalheUsaUmUnicoSelect() throws Exception {
        Consulta c = consulta(DIA.atTime(15, 0));
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            mvc.perform(get("/consultas/" + c.getId())).andExpect(status().isOk());
            assertEquals(1, stats.getPrepareStatementCount(), "pet, tutor e veterinario devem vir no mesmo SELECT");
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    @Test
    void endpointsExistentesContinuamFuncionando() throws Exception {
        Consulta c = consulta(DIA.atTime(16, 0));
        mvc.perform(get("/consultas").param("data", DIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(c.getId()))
                .andExpect(jsonPath("$[0].petNome").value("Rex"))
                .andExpect(jsonPath("$[0].tutorNome").value("Ana Souza"));
        mvc.perform(patch("/consultas/" + c.getId() + "/status").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMADA\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/consultas/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }
}
