package com.petcuidar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO de entrada de POST /tutores.
 * Regra de negocio (US1.1): so e salvo com e-mail valido e pelo menos 1 pet.
 */
public class TutorCadastroDTO {
    @NotBlank
    private String nome;

    @NotBlank
    @Email
    private String email;

    private String telefone;
    private String cpf;

    @NotEmpty(message = "E necessario cadastrar pelo menos um pet")
    private List<@NotNull(message = "Pet nao pode ser nulo") @Valid PetCadastroDTO> pets;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public List<PetCadastroDTO> getPets() { return pets; }
    public void setPets(List<PetCadastroDTO> pets) { this.pets = pets; }
}
