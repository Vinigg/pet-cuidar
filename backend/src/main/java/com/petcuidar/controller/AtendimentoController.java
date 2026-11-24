package com.petcuidar.controller;

import com.petcuidar.auth.Acesso;
import com.petcuidar.auth.Permissao;
import static com.petcuidar.auth.Perfil.VETERINARIO;
import com.petcuidar.dto.AtualizacaoAtendimentoRequestDTO;
import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.RegistroAtendimentoRequestDTO;
import com.petcuidar.service.AtendimentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para gerenciamento de atendimentos veterinários.
 * Endpoints para registrar, atualizar e consultar atendimentos clínicos.
 */
@RestController
@RequestMapping("/atendimentos")
@Permissao(VETERINARIO)
public class AtendimentoController {

    private final AtendimentoService atendimentoService;

    public AtendimentoController(AtendimentoService atendimentoService) {
        this.atendimentoService = atendimentoService;
    }

    /**
     * Registra um novo atendimento veterinário.
     * Veterinário autorizado registra descrição clínica vinculada ao pet e à consulta.
     * 
     * POST /atendimentos
     * 
     * @param request DTO com dados do novo atendimento
     * @return DTO com dados do atendimento registrado
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AtendimentoResponseDTO registrar(@Valid @RequestBody RegistroAtendimentoRequestDTO request,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) {
            acesso.verificarConsulta(request.getConsultaId());
            Acesso.exigir(acesso.conta().veterinarioId().equals(request.getVeterinarioId()));
        }
        return atendimentoService.registrarAtendimento(request);
    }

    /**
     * Atualiza a descrição clínica de um atendimento existente.
     * Alteração é rastreável através dos campos de auditoria.
     * 
     * PATCH /atendimentos/{id}
     * 
     * @param request DTO com dados da atualização
     * @return DTO com dados do atendimento atualizado
     */
    @PatchMapping
    public AtendimentoResponseDTO atualizar(@Valid @RequestBody AtualizacaoAtendimentoRequestDTO request,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) {
            acesso.verificarAtendimento(request.getAtendimentoId());
            Acesso.exigir(acesso.conta().veterinarioId().equals(request.getVeterinarioId()));
        }
        return atendimentoService.atualizarAtendimento(request);
    }

    /**
     * Obtém os detalhes de um atendimento específico.
     * Inclui informações de auditoria e rastreabilidade.
     * 
     * GET /atendimentos/{id}
     * 
     * @param id ID do atendimento
     * @return DTO com dados do atendimento
     */
    @GetMapping("/{id}")
    public AtendimentoResponseDTO obter(@PathVariable Long id,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) acesso.verificarAtendimento(id);
        return atendimentoService.obterAtendimento(id);
    }

    /**
     * Lista todos os atendimentos de um pet específico.
     * Ordenados por data descendente (mais recentes primeiro).
     * 
     * GET /atendimentos/pet/{petId}
     * 
     * @param petId ID do pet
     * @return Lista de DTOs dos atendimentos
     */
    @GetMapping("/pet/{petId}")
    public List<AtendimentoResponseDTO> listarPorPet(@PathVariable Long petId,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) {
            acesso.verificarPet(petId);
            return atendimentoService.listarAtendimentosPorPet(petId, acesso.conta().veterinarioId());
        }
        return atendimentoService.listarAtendimentosPorPet(petId);
    }
}
