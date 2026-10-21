package com.petcuidar.controller;

import com.petcuidar.dto.*;
import com.petcuidar.service.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/consultas")
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaResponseDTO criar(@Valid @RequestBody ConsultaRequestDTO dto) {
        return consultaService.criar(dto);
    }

    // GET /consultas?data=2026-09-22  -> painel diario da recepcionista (US2.2)
    @GetMapping
    public List<ConsultaResponseDTO> listarDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return consultaService.listarDoDia(data);
    }

    @GetMapping("/{id}/predicao-no-show")
    public PredicaoNoShowDTO predicaoNoShow(@PathVariable Long id) {
        return consultaService.obterPredicaoNoShow(id);
    }

    @PostMapping("/{id}/lembrete")
    public LembreteResponseDTO dispararLembrete(@PathVariable Long id) {
        return consultaService.dispararLembrete(id);
    }
}
