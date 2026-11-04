package com.petcuidar.service;

import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.ConsultaHistoricoDTO;
import com.petcuidar.dto.HistoricoPetDTO;
import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.model.Pet;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.service.ia.ResumoHistoricoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Leitura do prontuario do pet (US5.1): historico completo e resumo clinico.
 * A escrita de atendimentos fica em {@link AtendimentoService}.
 */
@Service
public class HistoricoAtendimentoService {

    private final PetRepository petRepository;
    private final HistoricoAtendimentoRepository historicoRepository;
    private final ConsultaRepository consultaRepository;
    private final ResumoHistoricoService resumoHistoricoService;

    public HistoricoAtendimentoService(PetRepository petRepository,
                                       HistoricoAtendimentoRepository historicoRepository,
                                       ConsultaRepository consultaRepository,
                                       ResumoHistoricoService resumoHistoricoService) {
        this.petRepository = petRepository;
        this.historicoRepository = historicoRepository;
        this.consultaRepository = consultaRepository;
        this.resumoHistoricoService = resumoHistoricoService;
    }

    /**
     * Historico completo do pet: todas as consultas (mais recentes primeiro),
     * cada uma com seus atendimentos, mais os registros sem consulta vinculada.
     */
    @Transactional(readOnly = true)
    public HistoricoPetDTO obterHistorico(Long petId) {
        Pet pet = buscarPet(petId);
        List<HistoricoAtendimento> registros = historicoRepository.findByPetIdOrderByDataDesc(petId);

        Map<Long, List<AtendimentoResponseDTO>> atendimentosPorConsulta = registros.stream()
                .filter(r -> r.getConsulta() != null)
                .collect(Collectors.groupingBy(r -> r.getConsulta().getId(),
                        Collectors.mapping(AtendimentoResponseDTO::from, Collectors.toList())));

        List<ConsultaHistoricoDTO> consultas = consultaRepository.findByPetIdOrderByDataHoraDesc(petId).stream()
                .map(c -> ConsultaHistoricoDTO.from(c,
                        atendimentosPorConsulta.getOrDefault(c.getId(), List.of())))
                .toList();

        List<AtendimentoResponseDTO> semConsulta = registros.stream()
                .filter(r -> r.getConsulta() == null)
                .map(AtendimentoResponseDTO::from)
                .toList();

        String itensCriticos = pet.getAlergiasCriticas() == null || pet.getAlergiasCriticas().isBlank()
                ? null : pet.getAlergiasCriticas();

        return new HistoricoPetDTO(
                pet.getId(),
                pet.getNome(),
                pet.getEspecie(),
                pet.getIdade(),
                pet.getTutor() != null ? pet.getTutor().getNome() : null,
                itensCriticos,
                consultas,
                semConsulta
        );
    }

    public ResumoHistoricoDTO obterResumo(Long petId) {
        Pet pet = buscarPet(petId);
        List<HistoricoAtendimento> historico = historicoRepository.findByPetIdOrderByDataDesc(petId);
        return resumoHistoricoService.gerar(pet, historico);
    }

    private Pet buscarPet(Long id) {
        return petRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pet nao encontrado: id " + id));
    }
}
