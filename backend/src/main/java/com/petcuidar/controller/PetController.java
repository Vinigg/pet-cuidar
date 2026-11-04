package com.petcuidar.controller;

import com.petcuidar.dto.HistoricoPetDTO;
import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.dto.PetOpcaoDTO;
import com.petcuidar.service.HistoricoAtendimentoService;
import com.petcuidar.service.PetService;
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
    public HistoricoPetDTO historico(@PathVariable Long id) {
        return historicoService.obterHistorico(id);
    }

    @GetMapping("/{id}/historico/resumo")
    public ResumoHistoricoDTO resumo(@PathVariable Long id) {
        return historicoService.obterResumo(id);
    }
}
