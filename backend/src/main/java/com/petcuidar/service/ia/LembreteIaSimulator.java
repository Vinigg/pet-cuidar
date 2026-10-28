package com.petcuidar.service.ia;

import org.springframework.stereotype.Component;

@Component
public class LembreteIaSimulator implements LembreteIaClient {
    @Override
    public String gerar(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario) {
        return String.format("Ola %s! Passando para lembrar que %s tem consulta marcada com %s no dia %s. Ate la!",
                nomeTutor, nomePet, veterinario, dataHoraFmt);
    }
}
