package com.petcuidar.controller;

import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.model.Tutor;
import com.petcuidar.service.TutorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tutores")
public class TutorController {

    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tutor cadastrar(@Valid @RequestBody TutorCadastroDTO dto) {
        return tutorService.cadastrar(dto);
    }
}
