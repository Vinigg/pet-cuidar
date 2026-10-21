package com.petcuidar.controller;

import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.service.PetService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pets")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoAtendimento> historico(@PathVariable Long id) {
        return petService.listarHistorico(id);
    }

    @GetMapping("/{id}/historico/resumo")
    public ResumoHistoricoDTO resumo(@PathVariable Long id) {
        return petService.obterResumo(id);
    }
}
