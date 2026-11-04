package com.petcuidar.service;

import com.petcuidar.dto.AtualizacaoAtendimentoRequestDTO;
import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.RegistroAtendimentoRequestDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciamento de atendimentos veterinários.
 * Responsável por registrar e atualizar descrições clínicas com rastreabilidade.
 */
@Service
public class AtendimentoService {

    private final HistoricoAtendimentoRepository historicoRepository;
    private final PetRepository petRepository;
    private final ConsultaRepository consultaRepository;
    private final VeterinarioRepository veterinarioRepository;

    public AtendimentoService(HistoricoAtendimentoRepository historicoRepository,
                              PetRepository petRepository,
                              ConsultaRepository consultaRepository,
                              VeterinarioRepository veterinarioRepository) {
        this.historicoRepository = historicoRepository;
        this.petRepository = petRepository;
        this.consultaRepository = consultaRepository;
        this.veterinarioRepository = veterinarioRepository;
    }

    /**
     * Registra um novo atendimento com descrição clínica.
     * Vincula o atendimento ao pet, consulta e veterinário responsável.
     * 
     * @param request DTO com dados do atendimento
     * @return DTO com dados do atendimento registrado
     * @throws RecursoNaoEncontradoException se pet, consulta ou veterinário não existirem
     */
    @Transactional
    public AtendimentoResponseDTO registrarAtendimento(RegistroAtendimentoRequestDTO request) {
        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pet com ID " + request.getPetId() + " não encontrado"));

        Consulta consulta = consultaRepository.findById(request.getConsultaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Consulta com ID " + request.getConsultaId() + " não encontrada"));

        Veterinario veterinario = veterinarioRepository.findById(request.getVeterinarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Veterinário com ID " + request.getVeterinarioId() + " não encontrado"));

        if (!veterinario.isAtivo()) {
            throw new IllegalArgumentException("Veterinário inativo não pode registrar atendimento");
        }

        HistoricoAtendimento atendimento = new HistoricoAtendimento();
        atendimento.setPet(pet);
        atendimento.setConsulta(consulta);
        atendimento.setVeterinario(veterinario);
        atendimento.setDescricao(request.getDescricaoClinica());
        atendimento.setData(LocalDateTime.now());
        atendimento.setVeterinarioCriacaoId(veterinario.getId());
        atendimento.setVeterinarioAtualizacaoId(veterinario.getId());

        HistoricoAtendimento atendimentoSalvo = historicoRepository.save(atendimento);
        return AtendimentoResponseDTO.from(atendimentoSalvo);
    }

    /**
     * Atualiza a descrição clínica de um atendimento existente.
     * Registra quem atualizou por último para rastreabilidade.
     * 
     * @param request DTO com dados da atualização
     * @return DTO com dados do atendimento atualizado
     * @throws RecursoNaoEncontradoException se atendimento ou veterinário não existirem
     */
    @Transactional
    public AtendimentoResponseDTO atualizarAtendimento(AtualizacaoAtendimentoRequestDTO request) {
        HistoricoAtendimento atendimento = historicoRepository.findById(request.getAtendimentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Atendimento com ID " + request.getAtendimentoId() + " não encontrado"));

        Veterinario veterinario = veterinarioRepository.findById(request.getVeterinarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Veterinário com ID " + request.getVeterinarioId() + " não encontrado"));

        if (!veterinario.isAtivo()) {
            throw new IllegalArgumentException("Veterinário inativo não pode atualizar atendimento");
        }

        atendimento.setDescricao(request.getDescricaoClinica());
        atendimento.setVeterinarioAtualizacaoId(veterinario.getId());

        HistoricoAtendimento atendimentoAtualizado = historicoRepository.save(atendimento);
        return AtendimentoResponseDTO.from(atendimentoAtualizado);
    }

    /**
     * Obtém os detalhes de um atendimento específico.
     * 
     * @param atendimentoId ID do atendimento
     * @return DTO com dados do atendimento
     * @throws RecursoNaoEncontradoException se atendimento não existir
     */
    @Transactional(readOnly = true)
    public AtendimentoResponseDTO obterAtendimento(Long atendimentoId) {
        HistoricoAtendimento atendimento = historicoRepository.findById(atendimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Atendimento com ID " + atendimentoId + " não encontrado"));
        return AtendimentoResponseDTO.from(atendimento);
    }

    /**
     * Lista todos os atendimentos de um pet específico, ordenados por data descendente.
     * 
     * @param petId ID do pet
     * @return Lista de DTOs dos atendimentos
     */
    @Transactional(readOnly = true)
    public List<AtendimentoResponseDTO> listarAtendimentosPorPet(Long petId) {
        return historicoRepository.findByPetIdOrderByDataDesc(petId)
                .stream()
                .map(AtendimentoResponseDTO::from)
                .collect(Collectors.toList());
    }
}
