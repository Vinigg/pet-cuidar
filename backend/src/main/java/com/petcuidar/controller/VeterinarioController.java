package com.petcuidar.controller;

import com.petcuidar.dto.DisponibilidadeDTO;
import com.petcuidar.model.Veterinario;
import com.petcuidar.repository.VeterinarioRepository;
import com.petcuidar.service.ConsultaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/veterinarios")
public class VeterinarioController {

    private final VeterinarioRepository veterinarioRepository;
    private final ConsultaService consultaService;

    public VeterinarioController(VeterinarioRepository veterinarioRepository, ConsultaService consultaService) {
        this.veterinarioRepository = veterinarioRepository;
        this.consultaService = consultaService;
    }

    @GetMapping
    public List<Veterinario> listar() {
        return veterinarioRepository.findAll();
    }

    // GET /veterinarios/{id}/disponibilidade?data=2026-09-22
    @GetMapping("/{id}/disponibilidade")
    public List<DisponibilidadeDTO> disponibilidade(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return consultaService.listarDisponibilidade(id, data);
    }
}
