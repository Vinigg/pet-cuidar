package com.petcuidar.controller;

import com.petcuidar.auth.Acesso;
import com.petcuidar.auth.Permissao;
import com.petcuidar.model.StatusConsulta;
import static com.petcuidar.auth.Perfil.*;
import com.petcuidar.dto.*;
import com.petcuidar.service.ConsultaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/consultas")
@Permissao(COLABORADOR)
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Permissao({COLABORADOR, TUTOR})
    public ConsultaResponseDTO criar(@Valid @RequestBody ConsultaRequestDTO dto,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) acesso.verificarPet(dto.getPetId());
        return consultaService.criar(dto);
    }

    // GET /consultas?data=2026-09-22  -> painel diario da recepcionista (US2.2)
    @GetMapping
    @Permissao({COLABORADOR, VETERINARIO})
    public List<ConsultaResponseDTO> listarDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null && acesso.eh(VETERINARIO)) {
            return consultaService.listarDoVeterinario(data, acesso.conta().veterinarioId());
        }
        return consultaService.listarDoDia(data);
    }

    @GetMapping("/minhas")
    @Permissao(TUTOR)
    public List<ConsultaResponseDTO> minhas(@RequestAttribute(Acesso.ATRIBUTO) Acesso acesso) {
        return consultaService.listarDoTutor(acesso.conta().tutorId());
    }

    @PatchMapping("/{id}/status")
    @Permissao({COLABORADOR, TUTOR})
    public ConsultaResponseDTO atualizarStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null && acesso.eh(TUTOR)) {
            Acesso.exigir(request.status() == StatusConsulta.CANCELADA);
            acesso.verificarConsulta(id);
        }
        return consultaService.atualizarStatus(id, request.status());
    }

    public record StatusRequest(@NotNull com.petcuidar.model.StatusConsulta status) {}

    // PATCH /consultas/{id}/reagendamento  {"dataHora":"2026-11-20T10:00:00"}  (PC-028)
    @PatchMapping("/{id}/reagendamento")
    @Permissao({COLABORADOR, TUTOR})
    public ConsultaResponseDTO reagendar(@PathVariable Long id, @Valid @RequestBody ReagendamentoRequestDTO request,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) acesso.verificarConsulta(id);
        return consultaService.reagendar(id, request.dataHora());
    }

    @GetMapping("/{id}/predicao-no-show")
    public PredicaoNoShowDTO predicaoNoShow(@PathVariable Long id) {
        return consultaService.obterPredicaoNoShow(id);
    }

    @PostMapping("/{id}/lembrete")
    public LembreteResponseDTO dispararLembrete(@PathVariable Long id) {
        return consultaService.dispararLembrete(id);
    }

    @GetMapping("/{id}/lembrete")
    public EstadoLembreteDTO estadoLembrete(@PathVariable Long id) {
        return consultaService.obterEstadoLembrete(id);
    }
}
