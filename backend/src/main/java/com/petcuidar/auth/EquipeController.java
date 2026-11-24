package com.petcuidar.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/equipe")
@Permissao(Perfil.ADMIN)
public class EquipeController {
    public record EmailConta(@NotBlank @Email @Size(max = 254) String email) {}
    public record NovoColaborador(@NotBlank @Size(max = 255) String nome,
                                  @NotBlank @Email @Size(max = 254) String email) {}

    private final ContasService contas;

    public EquipeController(ContasService contas) {
        this.contas = contas;
    }

    @GetMapping
    public List<ContasService.Membro> listar() {
        return contas.listarEquipe();
    }

    @PostMapping("/colaboradores")
    @ResponseStatus(HttpStatus.CREATED)
    public ContasService.Membro cadastrar(@Valid @RequestBody NovoColaborador pedido) {
        return contas.cadastrarColaborador(pedido.nome(), pedido.email());
    }

    @PutMapping("/veterinarios/{id}")
    public ContasService.Membro vincular(@PathVariable long id, @Valid @RequestBody EmailConta pedido) {
        return contas.vincularVeterinario(id, pedido.email());
    }
}
