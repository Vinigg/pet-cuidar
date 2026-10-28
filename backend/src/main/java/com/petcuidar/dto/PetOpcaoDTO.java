package com.petcuidar.dto;

public class PetOpcaoDTO {
    private final Long id;
    private final String nome;
    private final String especie;
    private final String tutorNome;

    public PetOpcaoDTO(Long id, String nome, String especie, String tutorNome) {
        this.id = id;
        this.nome = nome;
        this.especie = especie;
        this.tutorNome = tutorNome;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEspecie() { return especie; }
    public String getTutorNome() { return tutorNome; }
}