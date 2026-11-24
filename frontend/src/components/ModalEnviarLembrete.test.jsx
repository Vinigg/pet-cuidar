import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import ModalEnviarLembrete from './ModalEnviarLembrete';
import { dispararLembrete } from '../api/api';

jest.mock('../api/api', () => ({ dispararLembrete: jest.fn() }));
const consulta = id => ({ id, petNome: 'Pet ' + id, tutorNome: 'Tutor', dataHora: '2026-12-01T10:00:00' });
let container;
let root;
let callback;
beforeEach(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
    jest.resetAllMocks();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    callback = jest.fn();
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
});
const abrir = async (id = 1, isOpen = true) => act(async () => root.render(
    <ModalEnviarLembrete isOpen={isOpen} consulta={consulta(id)} onClose={() => {}} onLembreteEnviado={callback} />
));
const enviar = () => [...container.querySelectorAll('button')].find(b => b.textContent.includes('Enviar lembrete'));

test('usa textoEnviado, status e usouFallback do backend para confirmar o envio', async () => {
    dispararLembrete.mockResolvedValue({ data: { status: 'ENVIADO', textoEnviado: 'Mensagem real de fallback', usouFallback: true } });
    await abrir();
    expect(container.textContent).not.toContain('Lembrete Enviado');
    await act(async () => enviar().click());
    expect(dispararLembrete).toHaveBeenCalledWith(1);
    expect(container.textContent).toContain('Mensagem real de fallback');
    expect(container.textContent).toContain('Template padrão validado');
    expect(container.textContent).toContain('Lembrete Enviado');
    expect(callback).toHaveBeenCalledTimes(1);
    expect([...container.querySelectorAll('button')].find(b => b.textContent.includes('Lembrete Enviado')).disabled).toBe(true);
});

test.each([502, 504, 401])('erro HTTP %s nao fabrica texto nem confirma envio', async status => {
    dispararLembrete.mockRejectedValue({ response: { status, data: { mensagem: 'Falha real do servidor' } } });
    await abrir();
    await act(async () => enviar().click());
    expect(container.querySelector('[role="alert"]').textContent).toBe('Falha real do servidor');
    expect(container.textContent).not.toContain('Lembrete Enviado');
    expect(container.textContent).not.toContain('Olá,');
    expect(callback).not.toHaveBeenCalled();
});

test('um retorno PENDENTE nao e apresentado como enviado', async () => {
    dispararLembrete.mockResolvedValue({ data: { status: 'PENDENTE', textoEnviado: 'Aguardando envio' } });
    await abrir();
    await act(async () => enviar().click());
    expect(callback).not.toHaveBeenCalled();
    expect(container.querySelector('[role="alert"]').textContent).toContain('não foi confirmado');
    expect(container.textContent).not.toContain('Lembrete Enviado');
});

test('duplo clique gera apenas uma chamada e resultado anterior nao vaza para outra consulta', async () => {
    let resolver;
    dispararLembrete.mockImplementationOnce(() => new Promise(resolve => { resolver = resolve; }));
    await abrir();
    const botao = enviar();
    await act(async () => { botao.click(); botao.click(); });
    expect(dispararLembrete).toHaveBeenCalledTimes(1);
    await abrir(2);
    await act(async () => resolver({ data: { status: 'ENVIADO', textoEnviado: 'Mensagem da consulta antiga' } }));
    expect(container.textContent).toContain('Pet 2');
    expect(container.textContent).not.toContain('Mensagem da consulta antiga');
    expect(callback).not.toHaveBeenCalled();
    dispararLembrete.mockResolvedValue({ data: { status: 'ENVIADO', textoEnviado: 'Mensagem nova' } });
    await act(async () => enviar().click());
    expect(dispararLembrete).toHaveBeenLastCalledWith(2);
    expect(container.textContent).toContain('Mensagem nova');
    await abrir(2, false);
    await abrir(2);
    expect(container.textContent).not.toContain('Mensagem nova');
    expect(enviar().disabled).toBe(false);
});
