import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import MinhaAgenda from './MinhaAgenda';
import { obterMinhaAgenda } from '../api/api';

jest.mock('../api/api', () => ({ obterMinhaAgenda: jest.fn() }));

let container;
let root;
beforeEach(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
    jest.resetAllMocks();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
});

test('mostra horários livres, reservas, bloqueios e status das consultas', async () => {
    obterMinhaAgenda.mockResolvedValue({ data: {
        horarios: [
            { horario: '2099-01-01T08:00:00', disponivel: false },
            { horario: '2099-01-01T08:30:00', disponivel: true },
            { horario: '2099-01-01T09:00:00', disponivel: false }
        ],
        consultas: [
            { id: 1, dataHora: '2099-01-01T08:00:00', status: 'CONFIRMADA', petNome: 'Rex', tutorNome: 'Ana' },
            { id: 2, dataHora: '2099-01-01T08:30:00', status: 'CANCELADA', petNome: 'Luna', tutorNome: 'Bia' }
        ]
    } });
    await act(async () => root.render(<MinhaAgenda />));
    const slots = [...container.querySelectorAll('.agenda-horario')].map((item) => item.textContent);
    expect(slots).toEqual(['08:00ReservadoRex · Ana', '08:30Livre', '09:00Indisponível']);
    expect(container.querySelector('tbody').textContent).toContain('Confirmada');
    expect(container.querySelector('tbody').textContent).toContain('Cancelada');
    expect(obterMinhaAgenda).toHaveBeenCalledWith(container.querySelector('input[type="date"]').value);
});

test('troca o dia, descarta resposta atrasada e permite atualizar a agenda', async () => {
    let primeira;
    obterMinhaAgenda.mockImplementationOnce(() => new Promise((resolve) => { primeira = resolve; }))
        .mockResolvedValue({ data: { horarios: [], consultas: [] } });
    await act(async () => root.render(<MinhaAgenda />));
    expect(container.textContent).toContain('Carregando agenda');
    await act(async () => Simulate.change(container.querySelector('input'), { target: { value: '2099-01-02' } }));
    expect(obterMinhaAgenda).toHaveBeenLastCalledWith('2099-01-02');
    expect(container.textContent).toContain('Nenhuma consulta nesta data.');
    await act(async () => primeira({ data: {
        horarios: [], consultas: [{ id: 9, dataHora: '2099-01-01T09:00:00', petNome: 'Resposta antiga' }]
    } }));
    expect(container.textContent).not.toContain('Resposta antiga');
    await act(async () => container.querySelector('button').click());
    expect(obterMinhaAgenda).toHaveBeenCalledTimes(3);
});

test('informa erro e recupera a agenda ao tentar novamente', async () => {
    obterMinhaAgenda.mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValue({ data: { horarios: [], consultas: [] } });
    await act(async () => root.render(<MinhaAgenda />));
    expect(container.querySelector('[role="alert"]').textContent).toContain('Não foi possível carregar');
    await act(async () => container.querySelector('button').click());
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(container.textContent).toContain('Nenhum horário de atendimento nesta data.');
});
