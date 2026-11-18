package com.petcuidar.controller;

import com.petcuidar.auth.Acesso;
import com.petcuidar.auth.Permissao;
import static com.petcuidar.auth.Perfil.*;
import com.petcuidar.dto.*;
import com.petcuidar.service.AgendaVeterinarioService;
import com.petcuidar.service.VeterinarioService;
import com.petcuidar.service.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/veterinarios")
@Permissao(ADMIN)
public class VeterinarioController {

    private final VeterinarioService veterinarioService;
    private final AgendaVeterinarioService agenda;
    private final ConsultaService consultas;

    public VeterinarioController(VeterinarioService veterinarioService, AgendaVeterinarioService agenda,
                                 ConsultaService consultas) {
        this.veterinarioService = veterinarioService;
        this.agenda = agenda;
        this.consultas = consultas;
    }

    public record MinhaAgenda(LocalDate data, List<DisponibilidadeDTO> horarios,
                              List<ConsultaResponseDTO> consultas) {}

    @GetMapping("/minha-agenda")
    @Permissao(VETERINARIO)
    public MinhaAgenda minhaAgenda(@RequestAttribute(Acesso.ATRIBUTO) Acesso acesso,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Long veterinarioId = acesso.conta().veterinarioId();
        return new MinhaAgenda(data, agenda.listarGradeDoDia(veterinarioId, data),
                consultas.listarDoVeterinario(data, veterinarioId));
    }

    @GetMapping
    @Permissao({ADMIN, COLABORADOR, TUTOR, VETERINARIO})
    public List<VeterinarioResponseDTO> listar(@RequestParam(defaultValue = "true") boolean ativo,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null && acesso.eh(VETERINARIO)) {
            return List.of(veterinarioService.obter(acesso.conta().veterinarioId()));
        }
        return veterinarioService.listar(acesso != null && !acesso.eh(ADMIN) || ativo);
    }

    @GetMapping("/{id}")
    public VeterinarioResponseDTO obter(@PathVariable Long id) {
        return veterinarioService.obter(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VeterinarioResponseDTO cadastrar(@Valid @RequestBody VeterinarioCadastroDTO dto) {
        return veterinarioService.cadastrar(dto);
    }

    @PutMapping("/{id}")
    public VeterinarioResponseDTO atualizar(@PathVariable Long id,
                                           @Valid @RequestBody VeterinarioAtualizacaoDTO dto) {
        return veterinarioService.atualizar(id, dto);
    }

    @GetMapping("/{id}/horarios-atendimento")
    public List<HorarioAtendimentoDTO> horarios(@PathVariable Long id) {
        return veterinarioService.listarHorarios(id);
    }

    @PutMapping("/{id}/horarios-atendimento")
    public List<HorarioAtendimentoDTO> substituirHorarios(@PathVariable Long id,
                                                         @Valid @RequestBody EscalaVeterinarioDTO dto) {
        return veterinarioService.substituirHorarios(id, dto);
    }

    @GetMapping("/{id}/bloqueios")
    public List<BloqueioAgendaResponseDTO> bloqueios(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return veterinarioService.listarBloqueios(id, inicio, fim);
    }

    @PostMapping("/{id}/bloqueios")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioAgendaResponseDTO criarBloqueio(@PathVariable Long id,
                                                  @Valid @RequestBody BloqueioAgendaRequestDTO dto) {
        return veterinarioService.criarBloqueio(id, dto);
    }

    @DeleteMapping("/{id}/bloqueios/{bloqueioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerBloqueio(@PathVariable Long id, @PathVariable Long bloqueioId) {
        veterinarioService.removerBloqueio(id, bloqueioId);
    }

    // GET /veterinarios/{id}/disponibilidade?data=2026-09-22
    @GetMapping("/{id}/disponibilidade")
    @Permissao({ADMIN, COLABORADOR, TUTOR})
    public List<DisponibilidadeDTO> disponibilidade(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return agenda.listarDisponibilidade(id, data);
    }
}
