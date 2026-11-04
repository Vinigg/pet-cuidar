package com.petcuidar.controller;

import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.dto.PetOpcaoDTO;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.service.PetService;
import com.petcuidar.service.HistoricoAtendimentoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pets")
public class PetController {

    private final PetService petService;
    private final HistoricoAtendimentoService historicoService;

    public PetController(PetService petService, HistoricoAtendimentoService historicoService) {
        this.petService = petService;
        this.historicoService = historicoService;
    }

    @GetMapping
    public List<PetOpcaoDTO> listar(@RequestParam(required = false) Long tutorId) {
        return petService.listarOpcoes(tutorId);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoAtendimento> historico(@PathVariable Long id) {
        return historicoService.buscarHistoricoPorPet(id);
    }

    @GetMapping("/{id}/historico/resumo")
    public ResumoHistoricoDTO resumo(@PathVariable Long id) {
        return petService.obterResumo(id); 
    }
}
