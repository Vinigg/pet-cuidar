package com.petcuidar.service;

import com.petcuidar.config.EmailProperties;
import com.petcuidar.exception.OperacaoNaoPermitidaException;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.LembreteRepository;
import com.petcuidar.service.email.EmailDeliveryException;
import com.petcuidar.service.email.EmailSender;
import com.petcuidar.service.ia.LembreteGeneratorService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LembreteDeliveryServiceTest {

    @Test
    void repeteFalhasTransitoriasEMarcaEnviadoSomenteAposSucesso() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        EmailSender sender = (destino, assunto, texto, chave) -> {
            if (chamadas.incrementAndGet() < 3) {
                throw new EmailDeliveryException("HTTP 502", true);
            }
        };
        Fixture fixture = new Fixture(sender);

        var resposta = fixture.service.disparar(10L);

        assertEquals(StatusEnvioLembrete.ENVIADO, resposta.getStatus());
        assertEquals(3, resposta.getTentativas());
        assertEquals(3, chamadas.get());
        assertNull(resposta.getUltimoErro());
        assertNotNull(fixture.lembrete.get().getDataEnvio());
    }

    @Test
    void falhaPermanenteNaoFazRetryNemFingeSucesso() {
        EmailSender sender = (destino, assunto, texto, chave) -> {
            throw new EmailDeliveryException("HTTP 400", false);
        };
        Fixture fixture = new Fixture(sender);

        var resposta = fixture.service.disparar(10L);

        assertEquals(StatusEnvioLembrete.FALHOU, resposta.getStatus());
        assertEquals(1, resposta.getTentativas());
        assertEquals("HTTP 400", resposta.getUltimoErro());
        assertNull(fixture.lembrete.get().getDataEnvio());
    }

    @Test
    void chamadaRepetidaDepoisDeEnviadoEIdempotente() throws Exception {
        EmailSender sender = mock(EmailSender.class);
        Fixture fixture = new Fixture(sender);

        fixture.service.disparar(10L);
        fixture.service.disparar(10L);

        verify(sender, times(1)).enviar(anyString(), anyString(), anyString(), eq("lembrete-consulta-10"));
        verify(fixture.generator, times(1)).gerar(any());
    }

    @Test
    void consultaCanceladaERejeitadaAntesDeGerarOuEnviar() throws Exception {
        Consulta cancelada = mock(Consulta.class);
        when(cancelada.getStatus()).thenReturn(StatusConsulta.CANCELADA);
        EmailSender sender = mock(EmailSender.class);
        Fixture fixture = new Fixture(sender, cancelada);

        assertThrows(OperacaoNaoPermitidaException.class, () -> fixture.service.disparar(10L));
        verifyNoInteractions(sender, fixture.generator);
    }

    private static class Fixture {
        final ConsultaRepository consultas = mock(ConsultaRepository.class);
        final LembreteRepository lembretes = mock(LembreteRepository.class);
        final LembreteGeneratorService generator = mock(LembreteGeneratorService.class);
        final AtomicReference<Lembrete> lembrete = new AtomicReference<>();
        final LembreteDeliveryService service;

        Fixture(EmailSender sender) { this(sender, consulta()); }

        Fixture(EmailSender sender, Consulta consulta) {
            EmailProperties properties = new EmailProperties();
            properties.getRetry().setInitialBackoffMs(0);
            properties.getRetry().setMaxAttempts(3);
            when(consultas.findById(10L)).thenReturn(Optional.of(consulta));
            when(lembretes.findByConsultaId(10L)).thenAnswer(inv -> Optional.ofNullable(lembrete.get()));
            when(lembretes.saveAndFlush(any())).thenAnswer(inv -> {
                Lembrete value = inv.getArgument(0); lembrete.set(value); return value;
            });
            when(lembretes.save(any())).thenAnswer(inv -> {
                Lembrete value = inv.getArgument(0); lembrete.set(value); return value;
            });
            when(generator.gerar(consulta)).thenReturn(new LembreteGeneratorService.ResultadoLembrete(
                    "Texto IA", "Texto validado", false));
            service = new LembreteDeliveryService(consultas, lembretes, generator, sender, properties);
        }

        private static Consulta consulta() {
            Tutor tutor = new Tutor(); tutor.setNome("Ana"); tutor.setEmail("ana@example.com");
            Pet pet = new Pet(); pet.setNome("Rex"); pet.setTutor(tutor);
            Veterinario vet = new Veterinario(); vet.setNome("Dra. Bia");
            Consulta consulta = new Consulta(); consulta.setId(10L); consulta.setPet(pet);
            consulta.setVeterinario(vet); consulta.setDataHora(LocalDateTime.now().plusDays(1));
            return consulta;
        }
    }
}
