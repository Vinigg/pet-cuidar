package com.petcuidar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class PetCadastroDTO {
    @NotBlank
    private String nome;
    private String especie;
    @PositiveOrZero(message = "Idade nao pode ser negativa")
    private Integer idade;
    @Size(max = 1000, message = "Alergias devem ter no maximo 1000 caracteres")
    private String alergiasCriticas;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEspecie() { return especie; }
    public void setEspecie(String especie) { this.especie = especie; }
    public Integer getIdade() { return idade; }
    public void setIdade(Integer idade) { this.idade = idade; }
    public String getAlergiasCriticas() { return alergiasCriticas; }
    public void setAlergiasCriticas(String alergiasCriticas) { this.alergiasCriticas = alergiasCriticas; }
}
