package com.petcuidar.service;

import com.petcuidar.config.EmailProperties;
import com.petcuidar.dto.LembreteResponseDTO;
import com.petcuidar.exception.IntegracaoExternaIndisponivelException;
import com.petcuidar.exception.IntegracaoExternaTimeoutException;
import com.petcuidar.exception.OperacaoNaoPermitidaException;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.Lembrete;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.model.StatusEnvioLembrete;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.LembreteRepository;
import com.petcuidar.service.email.EmailDeliveryException;
import com.petcuidar.service.email.EmailSender;
import com.petcuidar.service.ia.LembreteGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LembreteDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(LembreteDeliveryService.class);

    private final ConsultaRepository consultaRepository;
    private final LembreteRepository lembreteRepository;
    private final LembreteGeneratorService generatorService;
    private final EmailSender emailSender;
    private final EmailProperties properties;

    public LembreteDeliveryService(ConsultaRepository consultaRepository,
                                   LembreteRepository lembreteRepository,
                                   LembreteGeneratorService generatorService,
                                   EmailSender emailSender,
                                   EmailProperties properties) {
        this.consultaRepository = consultaRepository;
        this.lembreteRepository = lembreteRepository;
        this.generatorService = generatorService;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    static final String MENSAGEM_FALHA_ENVIO = "Nao foi possivel entregar o lembrete por e-mail apos todas "
            + "as tentativas. O lembrete ficou registrado com status FALHOU.";
    static final String MENSAGEM_TIMEOUT_ENVIO = "O provedor de e-mail nao respondeu no tempo limite apos todas "
            + "as tentativas. O lembrete ficou registrado com status FALHOU.";

    /**
     * Envio manual (POST /consultas/{id}/lembrete). O e-mail nao tem fallback:
     * se a entrega terminar em FALHOU - nesta chamada ou em uma anterior -,
     * responde 504 quando a ultima tentativa estourou o tempo limite e 502 nos
     * demais casos. O lembrete continua gravado como FALHOU para auditoria.
     * Falha de IA nao entra aqui: o texto ja saiu do template (usouFallback).
     */
    public LembreteResponseDTO dispararManual(Long consultaId) {
        LembreteResponseDTO resultado = disparar(consultaId);
        if (resultado.getStatus() != StatusEnvioLembrete.FALHOU) {
            return resultado;
        }
        if (EmailDeliveryException.indicaTimeout(resultado.getUltimoErro())) {
            throw new IntegracaoExternaTimeoutException(MENSAGEM_TIMEOUT_ENVIO);
        }
        throw new IntegracaoExternaIndisponivelException(MENSAGEM_FALHA_ENVIO);
    }

    /**
     * Ponto unico de entrega usado tanto pelo endpoint manual quanto pelo job.
     * Nunca lanca excecao por falha de entrega: devolve o lembrete com status FALHOU.
     * A sincronizacao garante idempotencia dentro da instancia unica suportada
     * pelo SQLite; a restricao UNIQUE em lembretes.consulta_id protege o banco.
     */
    public synchronized LembreteResponseDTO disparar(Long consultaId) {
        Consulta consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta nao encontrada: id " + consultaId));
        if (consulta.getStatus() == StatusConsulta.CANCELADA) {
            throw new OperacaoNaoPermitidaException("Nao e permitido enviar lembrete de consulta cancelada.");
        }

        Lembrete lembrete = lembreteRepository.findByConsultaId(consultaId).orElse(null);
        if (lembrete != null && (lembrete.getStatusEnvio() == StatusEnvioLembrete.ENVIADO
                || lembrete.getStatusEnvio() == StatusEnvioLembrete.FALHOU)) {
            return LembreteResponseDTO.from(lembrete);
        }

        if (lembrete == null) {
            lembrete = criarPendente(consulta);
            lembrete = lembreteRepository.saveAndFlush(lembrete);
        } else if (lembrete.getStatusEnvio() == StatusEnvioLembrete.PENDENTE
                && (lembrete.getTextoValidado() == null || lembrete.getTextoValidado().isBlank())) {
            lembrete = preencherConteudo(lembrete, consulta);
            lembrete = lembreteRepository.saveAndFlush(lembrete);
        }

        int maxTentativas = Math.max(1, Math.min(3, properties.getRetry().getMaxAttempts()));
        long backoff = Math.max(0, properties.getRetry().getInitialBackoffMs());
        while (lembrete.getTentativas() < maxTentativas) {
            lembrete.setTentativas(lembrete.getTentativas() + 1);
            lembrete.setDataUltimaTentativa(LocalDateTime.now());
            lembrete.setUltimoErro(null);
            lembreteRepository.saveAndFlush(lembrete);

            try {
                String destinatario = consulta.getPet().getTutor().getEmail();
                emailSender.enviar(destinatario, properties.getSubject(), lembrete.getTextoValidado(),
                        "lembrete-consulta-" + consultaId);
                lembrete.setStatusEnvio(StatusEnvioLembrete.ENVIADO);
                lembrete.setDataEnvio(LocalDateTime.now());
                lembrete.setUltimoErro(null);
                return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
            } catch (EmailDeliveryException e) {
                lembrete.setUltimoErro(limitarErro(e.getMessage()));
                boolean podeTentarNovamente = e.isTransitoria() && lembrete.getTentativas() < maxTentativas;
                if (!podeTentarNovamente) {
                    lembrete.setStatusEnvio(StatusEnvioLembrete.FALHOU);
                    log.warn("Envio de lembrete falhou definitivamente para consulta {} apos {} tentativa(s)",
                            consultaId, lembrete.getTentativas());
                    return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
                }
                lembreteRepository.saveAndFlush(lembrete);
                if (!aguardar(backoff)) {
                    lembrete.setStatusEnvio(StatusEnvioLembrete.FALHOU);
                    lembrete.setUltimoErro("Retry de e-mail interrompido");
                    return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
                }
                backoff = Math.min(backoff * 2, 30_000);
            } catch (RuntimeException e) {
                lembrete.setUltimoErro("Falha inesperada no provedor de e-mail");
                if (lembrete.getTentativas() >= maxTentativas) {
                    lembrete.setStatusEnvio(StatusEnvioLembrete.FALHOU);
                    return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
                }
                lembreteRepository.saveAndFlush(lembrete);
                if (!aguardar(backoff)) {
                    lembrete.setStatusEnvio(StatusEnvioLembrete.FALHOU);
                    return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
                }
                backoff = Math.min(backoff * 2, 30_000);
            }
        }

        lembrete.setStatusEnvio(StatusEnvioLembrete.FALHOU);
        return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
    }

    private Lembrete criarPendente(Consulta consulta) {
        return preencherConteudo(new Lembrete(), consulta);
    }

    private Lembrete preencherConteudo(Lembrete lembrete, Consulta consulta) {
        LembreteGeneratorService.ResultadoLembrete resultado = generatorService.gerar(consulta);
        lembrete.setConsulta(consulta);
        lembrete.setTextoGeradoIa(resultado.textoGeradoIa);
        lembrete.setTextoValidado(resultado.textoValidado);
        lembrete.setUsouFallback(resultado.usouFallback);
        lembrete.setStatusEnvio(StatusEnvioLembrete.PENDENTE);
        return lembrete;
    }

    private boolean aguardar(long millis) {
        if (millis <= 0) return true;
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String limitarErro(String erro) {
        if (erro == null || erro.isBlank()) return "Falha no envio de e-mail";
        return erro.substring(0, Math.min(erro.length(), 1000));
    }
}
