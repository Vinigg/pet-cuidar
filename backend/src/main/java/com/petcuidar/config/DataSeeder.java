package com.petcuidar.config;

import com.petcuidar.model.Veterinario;
import com.petcuidar.repository.VeterinarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Popula alguns veterinarios na primeira execucao, para que o front-end
 * tenha dados para exibir sem precisar de um cadastro manual via banco.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final VeterinarioRepository veterinarioRepository;

    public DataSeeder(VeterinarioRepository veterinarioRepository) {
        this.veterinarioRepository = veterinarioRepository;
    }

    @Override
    public void run(String... args) {
        if (veterinarioRepository.count() == 0) {
            Veterinario v1 = new Veterinario();
            v1.setNome("Dr. Bruno Salgado");
            v1.setEspecialidade("Clinica geral");

            Veterinario v2 = new Veterinario();
            v2.setNome("Dra. Camila Moraes");
            v2.setEspecialidade("Dermatologia");

            veterinarioRepository.save(v1);
            veterinarioRepository.save(v2);
        }
    }
}
