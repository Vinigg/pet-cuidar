package com.petcuidar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "veterinarios")
@Getter
@Setter
@NoArgsConstructor
public class Veterinario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nome;

    private String especialidade;

    @Column(nullable = false)
    private boolean ativo = true;

    @Version
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Long versao;
}