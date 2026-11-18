package com.petcuidar.controller;

import com.petcuidar.auth.Acesso;
import com.petcuidar.auth.Permissao;
import static com.petcuidar.auth.Perfil.*;
import com.petcuidar.dto.PetCadastroDTO;
import com.petcuidar.dto.PetResponseDTO;
import com.petcuidar.dto.HistoricoPetDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    @Permissao({TUTOR, VETERINARIO, COLABORADOR})
    public List<PetOpcaoDTO> listar(@RequestParam(required = false) Long tutorId,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null && acesso.eh(VETERINARIO)) {
            return petService.listarOpcoesVeterinario(acesso.conta().veterinarioId());
        }
        return petService.listarOpcoes(acesso != null && acesso.eh(TUTOR) ? acesso.conta().tutorId() : tutorId);
    }

    @GetMapping("/meus")
    @Permissao(TUTOR)
    public List<PetResponseDTO> meus(@RequestAttribute(Acesso.ATRIBUTO) Acesso acesso) {
        return petService.listarDoTutor(acesso.conta().tutorId());
    }

    @PostMapping
    @Permissao(TUTOR)
    @ResponseStatus(HttpStatus.CREATED)
    public PetResponseDTO cadastrar(@RequestAttribute(Acesso.ATRIBUTO) Acesso acesso,
                                    @Valid @RequestBody PetCadastroDTO pet) {
        return petService.cadastrar(acesso.conta().tutorId(), pet);
    }

    @GetMapping("/{id}/historico")
    @Permissao(VETERINARIO)
    public HistoricoPetDTO historico(@PathVariable Long id,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) acesso.verificarPet(id);
        return historicoService.obterHistorico(id);
    }

    @GetMapping("/{id}/historico/resumo")
    @Permissao(VETERINARIO)
    public ResumoHistoricoDTO resumo(@PathVariable Long id,
            @RequestAttribute(name = Acesso.ATRIBUTO, required = false) Acesso acesso) {
        if (acesso != null) acesso.verificarPet(id);
        return historicoService.obterResumo(id);
    }
}
