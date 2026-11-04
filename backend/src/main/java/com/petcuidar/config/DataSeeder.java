package com.petcuidar.config;

import com.petcuidar.model.*;
import com.petcuidar.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Seed de dados de teste (tutores, pets, veterinarios, consultas, historico,
 * lembrete e predicao). Roda apenas se o banco estiver vazio e fora do perfil
 * "prod". Feito em Java (e nao em SQL) para que as datas sejam gravadas no
 * mesmo formato que o Hibernate usa nas consultas, e para que os status das
 * consultas passem pela ConsultaStateMachine.
 */
@Component
@Profile("!prod")
public class DataSeeder implements CommandLineRunner {

    private final TutorRepository tutorRepository;
    private final PetRepository petRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final ConsultaRepository consultaRepository;
    private final HistoricoAtendimentoRepository historicoRepository;
    private final LembreteRepository lembreteRepository;
    private final PredicaoNoShowRepository predicaoRepository;
    private final ConsultaStateMachine stateMachine;

    public DataSeeder(TutorRepository tutorRepository, PetRepository petRepository,
                      VeterinarioRepository veterinarioRepository, ConsultaRepository consultaRepository,
                      HistoricoAtendimentoRepository historicoRepository, LembreteRepository lembreteRepository,
                      PredicaoNoShowRepository predicaoRepository, ConsultaStateMachine stateMachine) {
        this.tutorRepository = tutorRepository;
        this.petRepository = petRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.consultaRepository = consultaRepository;
        this.historicoRepository = historicoRepository;
        this.lembreteRepository = lembreteRepository;
        this.predicaoRepository = predicaoRepository;
        this.stateMachine = stateMachine;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (veterinarioRepository.count() > 0 || tutorRepository.count() > 0) {
            return;
        }

        Veterinario bruno = vet("Dr. Bruno Salgado", "Clinica geral");
        Veterinario camila = vet("Dra. Camila Moraes", "Dermatologia");
        Veterinario rafael = vet("Dr. Rafael Teixeira", "Cardiologia");

        Tutor ana = tutor("Ana Beatriz Lima", "ana.lima@example.com", "(81) 99111-2233", "111.444.777-35");
        Tutor carlos = tutor("Carlos Eduardo Souza", "carlos.souza@example.com", "(81) 99222-3344", "529.982.247-25");
        Tutor marina = tutor("Marina Alves", "marina.alves@example.com", "(81) 99333-4455", "168.995.350-09");

        Pet rex = pet(ana, "Rex", "Cachorro", 5, "Alergia a dipirona");
        Pet mia = pet(ana, "Mia", "Gato", 2, null);
        Pet thor = pet(carlos, "Thor", "Cachorro", 8, "Reacao a penicilina");
        Pet luna = pet(marina, "Luna", "Gato", 3, null);

        LocalDate hoje = LocalDate.now();

        // Historico passado, para alimentar a predicao e o resumo do historico.
        Consulta c1 = consulta(thor, bruno, hoje.minusDays(40), 9, StatusConsulta.REALIZADA);
        Consulta c2 = consulta(thor, bruno, hoje.minusDays(20), 10, StatusConsulta.NAO_COMPARECEU);
        Consulta c3 = consulta(rex, camila, hoje.minusDays(30), 14, StatusConsulta.REALIZADA);
        Consulta c4 = consulta(luna, rafael, hoje.minusDays(10), 11, StatusConsulta.NAO_COMPARECEU);

        historico(rex, c3, "Dermatite alergica tratada com shampoo medicado. Retorno em 30 dias.",
                hoje.minusDays(30).atTime(14, 40));
        historico(thor, c1, "Check-up geral e vacina polivalente. Sem intercorrencias.",
                hoje.minusDays(40).atTime(9, 35));

        // Consultas futuras, em estados diferentes.
        Consulta f1 = consulta(rex, camila, hoje.plusDays(1), 9, StatusConsulta.AGENDADA);
        Consulta f2 = consulta(mia, bruno, hoje.plusDays(1), 10, StatusConsulta.AGENDADA);
        Consulta f3 = consulta(thor, bruno, hoje.plusDays(2), 15, StatusConsulta.AGENDADA);
        stateMachine.transicionar(f3, StatusConsulta.CONFIRMADA);
        consultaRepository.save(f3);
        Consulta f4 = consulta(luna, rafael, hoje.plusDays(3), 11, StatusConsulta.AGENDADA);

        // Um lembrete com texto da IA diferente do validado (caso de fallback)
        // e outro em que a IA foi aprovada, para exercitar a separacao.
        lembrete(f1, "Ola Ana, lembrete: Rex tem consulta amanha as 09:00 com a Dra. Camila Moraes.",
                "Ola Ana, lembrete: Rex tem consulta amanha as 09:00 com a Dra. Camila Moraes.", false);
        lembrete(f3, "Thor precisa estar em jejum de 12h e tomar um medicamento X.",
                "Lembrete: Thor tem consulta em " + f3.getDataHora().toLocalDate() + " as 15:00. Em caso de duvida, ligue para a clinica.",
                true);

        // Predicoes: score cru e usado guardados separadamente.
        predicao(f3, 0.5, 0.5, false, "1 falta(s) em 2 consulta(s) anteriores.");
        predicao(f4, 0.93, 0.93, false, "1 falta(s) em 1 consulta(s) anteriores.");
        predicao(f2, null, null, true, "Score indisponivel: preditor fora do ar.");
    }

    private Veterinario vet(String nome, String especialidade) {
        Veterinario v = new Veterinario();
        v.setNome(nome);
        v.setEspecialidade(especialidade);
        return veterinarioRepository.save(v);
    }

    private Tutor tutor(String nome, String email, String telefone, String cpf) {
        Tutor t = new Tutor();
        t.setNome(nome);
        t.setEmail(email);
        t.setTelefone(telefone);
        t.setCpf(cpf);
        return tutorRepository.save(t);
    }

    private Pet pet(Tutor tutor, String nome, String especie, int idade, String alergias) {
        Pet p = new Pet();
        p.setTutor(tutor);
        p.setNome(nome);
        p.setEspecie(especie);
        p.setIdade(idade);
        p.setAlergiasCriticas(alergias);
        return petRepository.save(p);
    }

    /** Cria a consulta como AGENDADA e leva ao status final pela maquina de estados. */
    private Consulta consulta(Pet pet, Veterinario vet, LocalDate dia, int hora, StatusConsulta destino) {
        Consulta c = new Consulta();
        c.setPet(pet);
        c.setVeterinario(vet);
        c.setDataHora(LocalDateTime.of(dia, LocalTime.of(hora, 0)));
        if (destino != StatusConsulta.AGENDADA) {
            stateMachine.transicionar(c, destino);
        }
        return consultaRepository.save(c);
    }

    private void historico(Pet pet, Consulta consulta, String descricao, LocalDateTime data) {
        HistoricoAtendimento h = new HistoricoAtendimento();
        h.setPet(pet);
        h.setConsulta(consulta);
        h.setDescricao(descricao);
        h.setData(data);
        historicoRepository.save(h);
    }

    private void lembrete(Consulta consulta, String textoIa, String textoValidado, boolean usouFallback) {
        Lembrete l = new Lembrete();
        l.setConsulta(consulta);
        l.setTextoGeradoIa(textoIa);
        l.setTextoValidado(textoValidado);
        l.setUsouFallback(usouFallback);
        l.setStatusEnvio(StatusEnvioLembrete.ENVIADO);
        l.setDataEnvio(LocalDateTime.now());
        lembreteRepository.save(l);
    }

    private void predicao(Consulta consulta, Double cru, Double usado, boolean fallback, String motivo) {
        PredicaoNoShow p = new PredicaoNoShow();
        p.setConsulta(consulta);
        p.setScoreCru(cru);
        p.setScoreUsado(usado);
        p.setUsouFallback(fallback);
        p.setMotivo(motivo);
        p.setDataCalculo(LocalDateTime.now());
        predicaoRepository.save(p);
    }
}
