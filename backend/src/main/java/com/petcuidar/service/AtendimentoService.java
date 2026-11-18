package com.petcuidar.service;

import com.petcuidar.dto.AtualizacaoAtendimentoRequestDTO;
import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.RegistroAtendimentoRequestDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.exception.TransicaoEstadoInvalidaException;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciamento de atendimentos veterinários.
 * Responsável por registrar e atualizar descrições clínicas com rastreabilidade.
 *
 * <p>Integração com a máquina de estados: registrar o atendimento marca a
 * consulta como REALIZADA via {@link ConsultaStateMachine}, na mesma transação
 * (se o histórico não for gravado, o status também não muda). Consulta já
 * REALIZADA aceita registros adicionais sem nova transição; CANCELADA ou
 * NAO_COMPARECEU é recusada com 409.</p>
 */
@Service
public class AtendimentoService {

    private final HistoricoAtendimentoRepository historicoRepository;
    private final PetRepository petRepository;
    private final ConsultaRepository consultaRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final ConsultaStateMachine consultaStateMachine;

    @Autowired
    public AtendimentoService(HistoricoAtendimentoRepository historicoRepository,
                              PetRepository petRepository,
                              ConsultaRepository consultaRepository,
                              VeterinarioRepository veterinarioRepository,
                              ConsultaStateMachine consultaStateMachine) {
        this.historicoRepository = historicoRepository;
        this.petRepository = petRepository;
        this.consultaRepository = consultaRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.consultaStateMachine = consultaStateMachine;
    }

    /** Conveniência para testes: máquina de estados com relógio do sistema e tolerância zero. */
    public AtendimentoService(HistoricoAtendimentoRepository historicoRepository,
                              PetRepository petRepository,
                              ConsultaRepository consultaRepository,
                              VeterinarioRepository veterinarioRepository) {
        this(historicoRepository, petRepository, consultaRepository, veterinarioRepository,
                new ConsultaStateMachine(java.time.Clock.systemDefaultZone(), Duration.ZERO));
    }

    /**
     * Registra um novo atendimento com descrição clínica.
     * Vincula o atendimento ao pet, consulta e veterinário responsável.
     * 
     * @param request DTO com dados do atendimento
     * @return DTO com dados do atendimento registrado
     * @throws RecursoNaoEncontradoException se pet, consulta ou veterinário não existirem
     * @throws IllegalArgumentException se a consulta não for do pet informado (400)
     * @throws TransicaoEstadoInvalidaException se a consulta estiver CANCELADA ou NAO_COMPARECEU (409)
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
        if (consulta.getPet() != null && !Objects.equals(consulta.getPet().getId(), pet.getId())) {
            throw new IllegalArgumentException("A consulta informada não pertence ao pet informado");
        }

        marcarComoRealizada(consulta);

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
     * AGENDADA/CONFIRMADA → REALIZADA pela máquina de estados. Uma consulta já
     * REALIZADA pode receber mais de um registro (ex.: complemento clínico).
     */
    private void marcarComoRealizada(Consulta consulta) {
        if (consulta.getStatus() == StatusConsulta.REALIZADA) {
            return;
        }
        if (consulta.getStatus() == StatusConsulta.CANCELADA || consulta.getStatus() == StatusConsulta.NAO_COMPARECEU) {
            throw new TransicaoEstadoInvalidaException(
                    "Nao e possivel registrar atendimento para consulta com status " + consulta.getStatus() + ".");
        }
        consultaStateMachine.transicionar(consulta, StatusConsulta.REALIZADA);
        consultaRepository.save(consulta);
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
