import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import AgendamentoForm from './AgendamentoForm';
import HistoricoPet from './HistoricoPet';
import * as api from '../api/api';

jest.mock('../api/api', () => ({
    listarPets: jest.fn(), listarVeterinarios: jest.fn(), listarDisponibilidade: jest.fn(),
    criarConsulta: jest.fn(), obterHistoricoPet: jest.fn(), obterResumoHistoricoPet: jest.fn(),
    registrarAtendimento: jest.fn(),
}));
const pets = [
    { id: 1, nome: 'Bob', especie: 'Gato', tutorId: 11, tutorNome: 'João' },
    { id: 2, nome: 'Bob', especie: 'Gato', tutorId: 22, tutorNome: 'João' },
    { id: 3, nome: 'Bob', especie: 'Gato', tutorId: 11, tutorNome: 'João' },
];
let root;
let container;
beforeEach(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
    jest.resetAllMocks();
    api.listarPets.mockResolvedValue({ data: pets });
    api.listarVeterinarios.mockResolvedValue({ data: [{ id: 9, nome: 'Veterinária' }] });
    api.listarDisponibilidade.mockResolvedValue({ data: [{ horario: '09:00', disponivel: true }] });
    api.obterHistoricoPet.mockResolvedValue({ data: { petId: 1, petNome: 'Bob', itensCriticos: 'Alergia do primeiro cadastro', consultas: [], atendimentosSemConsulta: [] } });
    api.obterResumoHistoricoPet.mockResolvedValue({ data: { resumo: 'Resumo do primeiro cadastro', usouFallback: true } });
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => { act(() => root.unmount()); container.remove(); });
const render = async element => act(async () => root.render(element));
const change = async (id, value) => act(async () => {
    const input = container.querySelector('#' + id);
    input.value = value; Simulate.change(input);
});
const valores = id => [...container.querySelectorAll('#' + id + ' option')].map(o => o.value);

test('historico exige tutor primeiro e separa tutores e pets com nomes iguais', async () => {
    await render(<HistoricoPet />);
    expect(valores('historico-tutor')).toEqual(['', '11', '22']);
    expect(container.querySelector('#historico-tutor').textContent).toContain('cadastro #11');
    expect(container.querySelector('#historico-tutor').textContent).toContain('cadastro #22');
    expect(container.querySelector('#historico-pet').disabled).toBe(true);
    expect(api.obterHistoricoPet).not.toHaveBeenCalled();
    await change('historico-tutor', '11');
    expect(valores('historico-pet')).toEqual(['', '1', '3']);
    expect(container.querySelector('#historico-pet').textContent).toContain('pet #1');
    expect(container.querySelector('#historico-pet').textContent).toContain('pet #3');
    await change('historico-pet', '1');
    expect(api.obterHistoricoPet).toHaveBeenCalledWith('1');
    expect(container.textContent).toContain('Alergia do primeiro cadastro');
    await change('historico-tutor', '22');
    expect(valores('historico-pet')).toEqual(['', '2']);
    expect(container.querySelector('#historico-pet').value).toBe('');
    expect(container.textContent).not.toContain('Alergia do primeiro cadastro');
    expect(container.textContent).not.toContain('Resumo do primeiro cadastro');
});

test('trocar tutor descarta resposta atrasada do historico anterior', async () => {
    let responder;
    api.obterHistoricoPet.mockImplementationOnce(() => new Promise(resolve => { responder = resolve; }));
    await render(<HistoricoPet />);
    await change('historico-tutor', '11'); await change('historico-pet', '1');
    await change('historico-tutor', '22');
    await act(async () => responder({ data: { petId: 1, petNome: 'Paciente antigo', itensCriticos: 'Alerta antigo', consultas: [] } }));
    expect(container.textContent).not.toContain('Paciente antigo');
    expect(container.textContent).not.toContain('Alerta antigo');
    expect(container.querySelector('#historico-pet').value).toBe('');
});

test('agendamento invalida pet e horario ao trocar tutor e envia o id do cadastro correto', async () => {
    api.criarConsulta.mockResolvedValue({ data: { id: 50, petNome: 'Bob' } });
    await render(<AgendamentoForm />);
    expect(container.querySelector('#agendamento-pet').disabled).toBe(true);
    await change('agendamento-tutor', '11'); await change('agendamento-pet', '3');
    await change('agendamento-veterinario', '9');
    await act(async () => container.querySelector('.slot-btn').click());
    await change('agendamento-tutor', '22');
    expect(container.querySelector('#agendamento-pet').value).toBe('');
    expect(container.querySelector('button[type="submit"]').disabled).toBe(true);
    await act(async () => Simulate.submit(container.querySelector('form')));
    expect(api.criarConsulta).not.toHaveBeenCalled();
    expect(valores('agendamento-pet')).toEqual(['', '2']);
    await change('agendamento-pet', '2');
    await act(async () => container.querySelector('.slot-btn').click());
    await act(async () => Simulate.submit(container.querySelector('form')));
    expect(api.criarConsulta).toHaveBeenCalledWith(expect.objectContaining({ petId: 2, veterinarioId: 9 }));
});

test('lista vazia e falha de carregamento nao liberam selecoes', async () => {
    api.listarPets.mockRejectedValueOnce(new Error('Offline'));
    await render(<HistoricoPet />);
    expect(container.querySelector('#historico-tutor').disabled).toBe(true);
    expect(container.querySelector('#historico-pet').disabled).toBe(true);
    expect(container.querySelector('[role="alert"]').textContent).toContain('Não foi possível carregar');
    await act(async () => root.unmount());
    root = createRoot(container);
    api.listarPets.mockResolvedValueOnce({ data: [] });
    await render(<HistoricoPet />);
    expect(container.textContent).toContain('Nenhum paciente vinculado');
    expect(container.querySelector('#historico-tutor').disabled).toBe(true);
});

test('area de agendamento do proprio tutor continua sem seletor de outro tutor', async () => {
    await render(<AgendamentoForm visualTutor />);
    expect(container.querySelector('#agendamento-tutor')).toBeNull();
    expect(container.querySelectorAll('.pet-pick-card')).toHaveLength(3);
});
