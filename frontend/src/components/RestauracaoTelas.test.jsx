import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import TutorHome from './tutor/TutorHome';
import TutorMeusPets from './tutor/TutorMeusPets';
import ConsultaAgendadaSucesso from './tutor/ConsultaAgendadaSucesso';
import PainelTutor from './tutor/PainelTutor';
import AgendamentoForm from './AgendamentoForm';
import AgendaSemanal from './AgendaSemanal';
import GestaoTutoresPets from './GestaoTutoresPets';
import LembretesRecepcao from './LembretesRecepcao';
import RegistroNoProntuario from './RegistroNoProntuario';
import CalendarioConsulta from './CalendarioConsulta';
import { diasDaSemana } from '../utils/datas';
import * as api from '../api/api';

jest.mock('../api/api', () => ({
    listarPets: jest.fn(), listarVeterinarios: jest.fn(), listarDisponibilidade: jest.fn(), criarConsulta: jest.fn(),
    listarMeusPets: jest.fn(), listarMinhasConsultas: jest.fn(), atualizarStatusConsulta: jest.fn(),
    listarConsultasDoDia: jest.fn(), listarTutores: jest.fn(), obterEstadoLembrete: jest.fn(),
    dispararLembrete: jest.fn(), registrarAtendimento: jest.fn(),
}));
let root;
let container;
const pets = [
    { id: 31, nome: 'Jade', especie: 'Gato', idade: 0, alergiasCriticas: 'Alergia registrada' },
    { id: 32, nome: 'Bento', especie: 'Cachorro', idade: 4 },
];
const consulta = (id, nome, status = 'AGENDADA') => ({
    id, petId: 31, petNome: nome, veterinarioId: 9, veterinarioNome: 'Veterinária real',
    tutorNome: 'Tutora real', dataHora: '2099-12-01T09:00:00', status,
});
beforeEach(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
    jest.resetAllMocks();
    container = document.createElement('div'); document.body.appendChild(container);
    root = createRoot(container);
    api.listarPets.mockResolvedValue({ data: pets });
    api.listarMeusPets.mockResolvedValue({ data: pets });
    api.listarMinhasConsultas.mockResolvedValue({ data: [consulta(71, 'Jade')] });
    api.listarVeterinarios.mockResolvedValue({ data: [{ id: 9, nome: 'Veterinária real', especialidade: 'Clínica geral' }] });
    api.listarDisponibilidade.mockResolvedValue({ data: [{ horario: '09:00', disponivel: true }, { horario: '09:30', disponivel: false }] });
});
afterEach(() => { act(() => root.unmount()); container.remove(); });
const render = async component => act(async () => { root.render(component); });
const flush = async () => { for (let i = 0; i < 6; i += 1) await act(async () => { await Promise.resolve(); }); };
const click = async text => {
    const button = [...container.querySelectorAll('button')].find(b => b.textContent.trim() === text);
    expect(button).toBeTruthy();
    await act(async () => button.click());
};
const change = async (selector, value) => act(async () => {
    const element = container.querySelector(selector);
    element.value = value; Simulate.change(element);
});

test('dashboard recupera cards e contador reais, consulta proxima e navegacao por id', async () => {
    const navegar = jest.fn();
    await render(<TutorHome conta={{ nome: 'Cliente real' }} pets={pets} consultas={[consulta(71, 'Jade')]} navegar={navegar} />);
    expect(container.querySelector('.tutor-home-grid')).not.toBeNull();
    expect(container.querySelectorAll('.pet-detailed-card')).toHaveLength(2);
    expect(container.textContent).toContain('2 pets cadastrados');
    expect(container.textContent).toContain('Alergia registrada');
    expect(container.textContent).not.toContain('Thor');
    await click('Ver detalhes');
    expect(navegar).toHaveBeenCalledWith('detalhes-consulta', 71);
});

test('busca e filtro de especie combinam sem perder os dados dos cards', async () => {
    await render(<TutorMeusPets pets={pets} onCadastrarOutroPet={() => {}} onAgendarConsulta={() => {}} />);
    await click('Gato');
    expect(container.querySelectorAll('.pet-detailed-card')).toHaveLength(1);
    expect(container.textContent).toContain('0 anos');
    await change('input', 'Bento');
    expect(container.textContent).toContain('Nenhum pet corresponde');
    await click('Todos (2)');
    expect(container.querySelectorAll('.pet-detailed-card')).toHaveLength(1);
    expect(container.querySelector('.pet-detailed-card').textContent).toContain('Bento');
});

test('lista de consultas cancela na API e preserva o agendamento quando a API rejeita', async () => {
    api.atualizarStatusConsulta.mockRejectedValueOnce({ response: { data: { mensagem: 'Cancelamento recusado' } } });
    api.atualizarStatusConsulta.mockResolvedValueOnce({ data: consulta(71, 'Jade', 'CANCELADA') });
    await render(<PainelTutor conta={{ nome: 'Tutora' }} aba="consultas" navegar={() => {}} />);
    await flush();
    await click('Cancelar'); await click('Confirmar cancelamento');
    expect(api.atualizarStatusConsulta).toHaveBeenCalledWith(71, 'CANCELADA');
    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    expect(container.querySelector('[role="alert"]').textContent).toBe('Cancelamento recusado');
    expect(container.querySelector('tbody').textContent).toContain('Agendada');
    await click('Confirmar cancelamento');
    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(container.querySelector('tbody').textContent).toContain('Cancelada');
});

test('agendamento em cards usa ids reais, bloqueia horario ocupado e atualiza apos 409', async () => {
    api.criarConsulta.mockRejectedValue({ response: { status: 409, data: { mensagem: 'Horário acabou de ser reservado' } } });
    await render(<AgendamentoForm visualTutor />);
    await click('🐈JadeGato');
    await act(async () => container.querySelector('.vet-card-option').click());
    await flush();
    expect(container.querySelectorAll('.slot-btn')[1].disabled).toBe(true);
    await act(async () => container.querySelector('.slot-btn').click());
    const chamadas = api.listarDisponibilidade.mock.calls.length;
    await act(async () => Simulate.submit(container.querySelector('form')));
    expect(api.criarConsulta).toHaveBeenCalledWith(expect.objectContaining({ petId: 31, veterinarioId: 9 }));
    expect(api.listarDisponibilidade.mock.calls.length).toBe(chamadas + 1);
    expect(container.textContent).toContain('Horário acabou de ser reservado');
    expect(container.querySelector('.booking-summary-side-card').textContent).toContain('Jade');
});

test('confirmacao mantem apresentacao e usa somente consulta persistida', async () => {
    await render(<ConsultaAgendadaSucesso dados={consulta(71, 'Jade')} onVerConsultas={() => {}} onVoltarInicio={() => {}} />);
    expect(container.querySelector('.success-illustration-hero')).not.toBeNull();
    expect(container.querySelector('.restored-consult-card').textContent).toContain('Veterinária real');
    expect(container.textContent).not.toContain('Sala 03');
    expect(container.textContent).not.toContain('WhatsApp');
});

test('calendario usa datas reais e navega entre meses sem slots ficticios', async () => {
    const selecionar = jest.fn();
    await render(<CalendarioConsulta data="2099-02-01" onSelecionar={selecionar} />);
    expect(container.querySelectorAll('.calendar-days-grid button')).toHaveLength(28);
    await act(async () => container.querySelector('button[aria-label="28/02/2099"]').click());
    expect(selecionar).toHaveBeenCalledWith('2099-02-28');
    await act(async () => container.querySelector('button[aria-label="Próximo mês"]').click());
    expect(container.querySelectorAll('.calendar-days-grid button')).toHaveLength(31);
});

test('agenda semanal consulta sete dias reais e nao chama espacos vazios de horarios livres', async () => {
    const dias = diasDaSemana();
    api.listarConsultasDoDia.mockImplementation(async data => ({ data: data === dias[0].iso ? [{ ...consulta(71, 'Jade'), dataHora: dias[0].iso + 'T09:00:00' }] : [] }));
    await render(<AgendaSemanal onNovaConsulta={() => {}} />);
    await flush();
    expect(api.listarConsultasDoDia.mock.calls.map(args => args[0])).toEqual(dias.map(d => d.iso));
    expect(container.querySelectorAll('.weekly-event-pill')).toHaveLength(1);
    expect(container.textContent).toContain('Jade');
    expect(container.textContent).toContain('A disponibilidade para reservar é validada');
});

test('cadastros preservam fichas distintas mesmo com tutores homonimos', async () => {
    api.listarTutores.mockResolvedValue({ data: [
        { id: 1, nome: 'Ana', email: 'primeira@example.invalid', pets: [pets[0]] },
        { id: 2, nome: 'Ana', email: 'segunda@example.invalid', pets: [pets[1]] },
    ] });
    await render(<GestaoTutoresPets onNovoTutor={() => {}} />);
    await flush();
    await act(async () => container.querySelectorAll('tbody button')[1].click());
    expect(container.querySelector('aside').textContent).toContain('segunda@example.invalid');
    expect(container.querySelector('aside').textContent).toContain('Bento');
    expect(container.querySelector('aside').textContent).not.toContain('Jade');
});

test('consulta de lembrete abre a mensagem persistida sem disparar outro email', async () => {
    api.listarConsultasDoDia.mockResolvedValue({ data: [consulta(71, 'Jade')] });
    api.obterEstadoLembrete.mockResolvedValue({ data: { status: 'ENVIADO', textoEnviado: 'Texto que foi enviado', usouFallback: true } });
    await render(<LembretesRecepcao />);
    await flush(); await click('Ver mensagem');
    expect(container.querySelector('[role="dialog"]').textContent).toContain('Texto que foi enviado');
    expect(api.dispararLembrete).not.toHaveBeenCalled();
    expect([...container.querySelectorAll('button')].find(b => b.textContent === 'Lembrete Enviado ✓').disabled).toBe(true);
});

test('disparo em lote conta apenas sucessos reais e nao reenvia ja enviados', async () => {
    api.listarConsultasDoDia.mockResolvedValue({ data: [consulta(1, 'Jade'), consulta(2, 'Bento'), consulta(3, 'Zoe')] });
    api.obterEstadoLembrete.mockImplementation(async id => ({ data: { status: id === 3 ? 'ENVIADO' : 'NAO_GERADO' } }));
    api.dispararLembrete.mockImplementation(async id => {
        if (id === 2) throw new Error('Falha no SMTP');
        return { data: { status: 'ENVIADO' } };
    });
    await render(<LembretesRecepcao />); await flush(); await click('Disparo em lote'); await flush();
    expect(api.dispararLembrete.mock.calls.map(args => args[0])).toEqual([1, 2]);
    expect(container.textContent).toContain('1 lembrete(s) confirmado(s) pelo servidor; 1 falha(s).');
});

test('registro lateral filtra consultas do veterinario e salva evolucao na API', async () => {
    const onSalvo = jest.fn();
    api.registrarAtendimento.mockResolvedValue({ data: {} });
    await render(<RegistroNoProntuario petId={31} petNome="Jade" veterinarioId={9} onSalvo={onSalvo}
        consultas={[{ consultaId: 71, veterinarioId: 9, status: 'AGENDADA', dataHora: '2099-12-01T09:00:00' },
            { consultaId: 72, veterinarioId: 10, status: 'AGENDADA', dataHora: '2099-12-01T10:00:00' }]} />);
    expect(container.querySelector('option[value="72"]')).toBeNull();
    await change('select', '71'); await change('textarea', 'Evolução clínica preenchida pelo veterinário.');
    await act(async () => Simulate.submit(container.querySelector('form')));
    expect(api.registrarAtendimento).toHaveBeenCalledWith({
        petId: 31, consultaId: 71, veterinarioId: 9,
        descricaoClinica: 'Consulta de retorno\n\nEvolução clínica preenchida pelo veterinário.',
    });
    expect(onSalvo).toHaveBeenCalledTimes(1);
});

test('falha ao salvar evolucao preserva anotacoes sem mostrar sucesso', async () => {
    api.registrarAtendimento.mockRejectedValue({ response: { data: { mensagem: 'Consulta não pode receber atendimento' } } });
    const onSalvo = jest.fn();
    await render(<RegistroNoProntuario petId={31} petNome="Jade" veterinarioId={9} onSalvo={onSalvo}
        consultas={[{ consultaId: 71, veterinarioId: 9, status: 'AGENDADA', dataHora: '2099-12-01T09:00:00' }]} />);
    await change('select', '71'); await change('textarea', 'Descrição que deve permanecer preenchida.');
    await act(async () => Simulate.submit(container.querySelector('form')));
    expect(onSalvo).not.toHaveBeenCalled();
    expect(container.querySelector('textarea').value).toBe('Descrição que deve permanecer preenchida.');
    expect(container.querySelector('[role="alert"]').textContent).toContain('Consulta não pode receber atendimento');
});
