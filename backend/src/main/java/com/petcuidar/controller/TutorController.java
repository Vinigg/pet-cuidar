package com.petcuidar.controller;

import com.petcuidar.auth.Permissao;
import static com.petcuidar.auth.Perfil.COLABORADOR;

import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.dto.TutorResponseDTO;
import com.petcuidar.auth.CadastroTutorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tutores")
@Permissao(COLABORADOR)
public class TutorController {

    private final CadastroTutorService tutorService;

    public TutorController(CadastroTutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TutorResponseDTO cadastrar(@Valid @RequestBody TutorCadastroDTO dto) {
        return tutorService.cadastrarPeloColaborador(dto);
    }

    @GetMapping
    public java.util.List<TutorResponseDTO> listar() {
        return tutorService.listarCadastros();
    }
}
