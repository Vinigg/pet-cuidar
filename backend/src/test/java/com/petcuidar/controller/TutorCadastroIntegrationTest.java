package com.petcuidar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petcuidar.dto.PetCadastroDTO;
import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.TutorRepository;
import com.petcuidar.service.TutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TutorCadastroIntegrationTest {
    private static final String DB = "jdbc:sqlite:target/cadastro-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void bancoIsolado(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TutorRepository tutores;
    @Autowired PetRepository pets;
    @Autowired TutorService service;

    @BeforeEach
    void limparCadastros() {
        pets.deleteAll();
        tutores.deleteAll();
    }

    private ObjectNode cadastro(String email, String cpf) throws Exception {
        ObjectNode node = (ObjectNode) mapper.readTree("""
                {"nome":"Arthur", "email":"arthur@example.com", "telefone":"85999999999",
                 "pets":[{"nome":"Rex","especie":"Cachorro","idade":3,"alergiasCriticas":"Dipirona"},
                         {"nome":"Luna","especie":"Gato","idade":0}]}
                """);
        node.put("email", email);
        if (cpf != null) node.put("cpf", cpf);
        return node;
    }

    @Test
    void cadastraTutorComDoisPetsERetornaIdsSemCiclo() throws Exception {
        var resultado = mvc.perform(post("/tutores").contentType(APPLICATION_JSON)
                        .content(cadastro("arthur@example.com", "12345678900").toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("arthur@example.com"))
                .andExpect(jsonPath("$.pets.length()").value(2))
                .andExpect(jsonPath("$.pets[0].id").isNumber())
                .andExpect(jsonPath("$.pets[0].alergiasCriticas").value("Dipirona"))
                .andExpect(jsonPath("$.pets[1].idade").value(0))
                .andExpect(jsonPath("$.pets[0].tutor").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andReturn();
        var json = mapper.readTree(resultado.getResponse().getContentAsString());
        long tutorId = json.get("id").asLong();
        assertTrue(tutores.existsById(tutorId));
        assertEquals(2, pets.count());
        for (var pet : json.get("pets")) {
            var salvo = pets.findById(pet.get("id").asLong()).orElseThrow();
            assertEquals(tutorId, salvo.getTutor().getId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "nome", "semPets", "petsVazios", "petsNulos", "petNulo",
            "nomePet", "idade", "alergias"})
    void rejeitaDadosInvalidosSemSalvar(String caso) throws Exception {
        ObjectNode node = cadastro("arthur@example.com", null);
        ObjectNode pet = (ObjectNode) node.withArray("pets").get(0);
        switch (caso) {
            case "email" -> node.put("email", "email-invalido");
            case "nome" -> node.put("nome", "   ");
            case "semPets" -> node.remove("pets");
            case "petsVazios" -> node.putArray("pets");
            case "petsNulos" -> node.putNull("pets");
            case "petNulo" -> node.putArray("pets").addNull();
            case "nomePet" -> pet.put("nome", "   ");
            case "idade" -> pet.put("idade", -1);
            case "alergias" -> pet.put("alergiasCriticas", "a".repeat(1001));
            default -> throw new IllegalArgumentException(caso);
        }
        mvc.perform(post("/tutores").contentType(APPLICATION_JSON).content(node.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertEquals(0, tutores.count());
        assertEquals(0, pets.count());
    }

    @Test
    void rejeitaJsonMalformado() throws Exception {
        mvc.perform(post("/tutores").contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0, tutores.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "cpf"})
    void duplicidadeRetorna409SemCriarNovosPets(String campo) throws Exception {
        mvc.perform(post("/tutores").contentType(APPLICATION_JSON)
                        .content(cadastro("arthur@example.com", "12345678900").toString()))
                .andExpect(status().isCreated());
        var segundo = cadastro(campo.equals("email") ? "arthur@example.com" : "outro@example.com",
                campo.equals("cpf") ? "12345678900" : "98765432100");
        mvc.perform(post("/tutores").contentType(APPLICATION_JSON).content(segundo.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value(campo.equals("email")
                        ? "E-mail ja cadastrado." : "CPF ja cadastrado."));
        assertEquals(1, tutores.count());
        assertEquals(2, pets.count());
    }

    @Test
    void permiteMultiplosTutoresSemCpf() throws Exception {
        String[] cpfs = {"", "   ", null};
        for (int i = 0; i < cpfs.length; i++) {
            mvc.perform(post("/tutores").contentType(APPLICATION_JSON)
                            .content(cadastro("tutor" + i + "@example.com", cpfs[i]).toString()))
                    .andExpect(status().isCreated());
        }
        assertEquals(3, tutores.count());
        assertTrue(tutores.findAll().stream().allMatch(tutor -> tutor.getCpf() == null));
    }

    @Test
    void falhaNaPersistenciaDePetDesfazCadastroInteiro() {
        var valido = new PetCadastroDTO();
        valido.setNome("Rex");
        var invalido = new PetCadastroDTO();
        invalido.setNome(" ");
        var entrada = new TutorCadastroDTO();
        entrada.setNome("Arthur");
        entrada.setEmail("arthur@example.com");
        entrada.setPets(List.of(valido, invalido));
        assertThrows(jakarta.validation.ConstraintViolationException.class,
                () -> service.cadastrar(entrada));
        assertEquals(0, tutores.count());
        assertEquals(0, pets.count());
    }
}