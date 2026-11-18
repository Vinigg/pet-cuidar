package com.petcuidar.auth;

public record Conta(long id, String email, String nome, Perfil perfil, Long tutorId, Long veterinarioId) {}
