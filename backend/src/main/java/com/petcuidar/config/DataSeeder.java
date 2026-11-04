package com.petcuidar.config;

import com.petcuidar.model.Veterinario;
import com.petcuidar.model.HorarioAtendimentoVeterinario;
import com.petcuidar.repository.HorarioAtendimentoVeterinarioRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Popula alguns veterinarios na primeira execucao, para que o front-end
 * tenha dados para exibir sem precisar de um cadastro manual via banco.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final VeterinarioRepository veterinarioRepository;
    private final HorarioAtendimentoVeterinarioRepository horarios;

    public DataSeeder(VeterinarioRepository veterinarioRepository, HorarioAtendimentoVeterinarioRepository horarios) {
        this.veterinarioRepository = veterinarioRepository;
        this.horarios = horarios;
    }

    @Override
    @Transactional
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
            for (Veterinario veterinario : List.of(v1, v2)) {
                for (DayOfWeek dia : DayOfWeek.values()) {
                    horarios.save(new HorarioAtendimentoVeterinario(veterinario, dia,
                            LocalTime.of(8, 0), LocalTime.of(18, 0)));
                }
            }
        }
    }
}
