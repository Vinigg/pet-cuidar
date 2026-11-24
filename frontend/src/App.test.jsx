import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { consultarSessao, concluirCadastroTutor, sair, solicitarCodigo, verificarCodigo, obterMinhaAgenda } from './api/api';

jest.mock('./api/api', () => ({
    api: { interceptors: { response: { use: jest.fn(), eject: jest.fn() } } },
    consultarSessao: jest.fn(),
    solicitarCodigo: jest.fn(),
    verificarCodigo: jest.fn(),
    concluirCadastroTutor: jest.fn(),
    sair: jest.fn(),
    listarMeusPets: jest.fn(async () => ({ data: [] })),
    listarMinhasConsultas: jest.fn(async () => ({ data: [] })),
    listarPets: jest.fn(async () => ({ data: [] })),
    listarVeterinarios: jest.fn(async () => ({ data: [] })),
    listarEquipe: jest.fn(async () => ({ data: [] })),
    obterMinhaAgenda: jest.fn(async () => ({ data: { horarios: [], consultas: [] } }))
}));
jest.mock('./components/PainelRecepcionista', () => () => <h1>Agenda da recepção</h1>);
const mockMontarHistorico = jest.fn();
const mockMontarVeterinarios = jest.fn();

jest.mock('./components/HistoricoPet', () => () => {
    mockMontarHistorico();
    return <h1>Histórico veterinário</h1>;
});
jest.mock('./components/FormularioAtendimento', () => () => <h1>Atendimento veterinário</h1>);
jest.mock('./components/Veterinarios', () => () => {
    mockMontarVeterinarios();
    return <h1>Gestão de veterinários</h1>;
});

const obterConta = (perfil) => ({ id: 1, email: 'ana@example.com', nome: 'Ana', perfil });
let container;
let root;

beforeEach(() => {
    jest.clearAllMocks();
    obterMinhaAgenda.mockResolvedValue({ data: { horarios: [], consultas: [] } });
    global.IS_REACT_ACT_ENVIRONMENT = true;
    window.history.replaceState(null, '', '#/');
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
});

afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
});

async function abrir(perfil, rota = '/') {
    if (perfil) consultarSessao.mockResolvedValue({ data: obterConta(perfil) });
    else consultarSessao.mockRejectedValue({ response: { status: 401 } });
    window.history.replaceState(null, '', '#' + rota);
    await act(async () => root.render(<App />));
}

const menu = () => [...container.querySelectorAll('nav button')].map((item) => item.textContent.trim());

async function clicar(texto) {
    const elemento = [...container.querySelectorAll('button')].find((botao) => botao.textContent.trim() === texto);
    expect(elemento).toBeTruthy();
    await act(async () => elemento.click());
}

async function preencher(rotulo, valor) {
    const label = [...container.querySelectorAll('label')].find((item) => item.textContent.trim() === rotulo);
    expect(label?.control).toBeTruthy();
    await act(async () => {
        Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(label.control, valor);
        label.control.dispatchEvent(new Event('input', { bubbles: true }));
    });
}

async function informarCodigo() {
    solicitarCodigo.mockResolvedValue({ data: { mensagem: 'Confira o e-mail.' } });
    await preencher('E-mail', 'ana@example.com');
    await clicar('Enviar código por e-mail');
    expect(container.textContent).toContain('Confira o e-mail.');
    await preencher('Código de acesso', '12345678');
    await clicar('Verificar código');
}

test('C4 recarregar restaura o perfil a partir do servidor', async () => {
    await abrir('VETERINARIO', '/veterinario/atendimentos');
    expect(consultarSessao).toHaveBeenCalledTimes(1);
    expect(container.querySelector('main h1').textContent).toBe('Atendimento veterinário');
    expect(window.location.hash).toBe('#/veterinario/atendimentos');
});

test.each([
    ['C5 Tutor vê somente sua navegação', 'TUTOR', ['Início', 'Meus pets', 'Consultas'], 'Olá, Ana!'],
    ['C6 Veterinário vê somente sua navegação', 'VETERINARIO', ['Minha agenda', 'Histórico do pet', 'Registrar atendimento'], 'Histórico veterinário'],
    ['C7 Colaborador vê somente sua navegação', 'COLABORADOR', ['Painel do dia', 'Agendar consulta', 'Cadastro'], 'Agenda da recepção'],
    ['C8 Admin vê somente a gestão de equipe', 'ADMIN', ['Veterinários', 'Contas da equipe'], 'Gestão de veterinários']
])('%s', async (_, perfil, esperado, titulo) => {
    await abrir(perfil);
    expect(menu()).toEqual(esperado);
    expect(container.querySelector('main h1').textContent).toBe(titulo);

    if (perfil === 'TUTOR') {
        await clicar('Meus pets');
        await clicar('Cadastrar pet');
        expect(window.location.hash).toBe('#/tutor/cadastrar-pet');
        expect(container.textContent).toContain('Salvar pet');
        await clicar('Início');
        await clicar('Agendar consulta');
        expect(window.location.hash).toBe('#/tutor/agendar-consulta');
        expect(container.querySelector('main h1').textContent).toBe('Agendar Consulta');
    } else if (perfil === 'VETERINARIO') {
        await clicar('Minha agenda');
        expect(window.location.hash).toBe('#/veterinario/agenda');
        expect(container.querySelector('main h1').textContent).toBe('Minha agenda');
        await clicar('Registrar atendimento');
        expect(window.location.hash).toBe('#/veterinario/atendimentos');
        expect(container.querySelector('main h1').textContent).toBe('Atendimento veterinário');
    } else if (perfil === 'COLABORADOR') {
        await clicar('Cadastro');
        expect(container.querySelector('main h1').textContent).toBe('Cadastro de Tutor e Pet');
        await clicar('Agendar consulta');
        expect(container.querySelector('main h1').textContent).toBe('Agendar Consulta');
    } else {
        await clicar('Contas da equipe');
        expect(window.location.hash).toBe('#/admin/colaboradores');
        expect(container.querySelector('main h1').textContent).toBe('Contas da equipe');
        expect(container.textContent).toContain('Cadastrar colaborador');
        expect(container.textContent).toContain('Vincular acesso de veterinário');
    }
});

test('C9 visitante é enviado à entrada', async () => {
    let rejeitarSessao;
    consultarSessao.mockImplementation(() => new Promise((_, reject) => { rejeitarSessao = reject; }));
    window.history.replaceState(null, '', '#/admin/veterinarios');
    await act(async () => root.render(<App />));
    expect(container.querySelector('[role="status"]').textContent).toBe('Verificando sessão…');
    expect(mockMontarVeterinarios).not.toHaveBeenCalled();
    await act(async () => rejeitarSessao({ response: { status: 401 } }));
    expect(window.location.hash).toBe('#/login');
    expect(container.querySelector('main h2').textContent).toBe('Entrar');
    expect(container.textContent).not.toContain('Gestão de veterinários');
    expect(mockMontarVeterinarios).not.toHaveBeenCalled();
});

test('C10 URL de outro perfil mostra acesso negado', async () => {
    await abrir('COLABORADOR', '/veterinario/historico');
    expect(container.querySelector('main h1').textContent).toBe('Acesso negado');
    expect(container.textContent).not.toContain('Histórico veterinário');
    expect(mockMontarHistorico).not.toHaveBeenCalled();
    await clicar('Voltar ao meu painel');
    expect(window.location.hash).toBe('#/colaborador/painel');
    expect(container.querySelector('main h1').textContent).toBe('Agenda da recepção');
});

test('o login verifica o código, usa o perfil do servidor e sai via API', async () => {
    await abrir(null);
    verificarCodigo.mockResolvedValue({ data: obterConta('ADMIN') });
    sair.mockResolvedValue({});
    await informarCodigo();

    expect(solicitarCodigo).toHaveBeenCalledWith('ana@example.com', 'LOGIN');
    expect(verificarCodigo).toHaveBeenCalledWith('ana@example.com', 'LOGIN', '12345678');
    expect(window.location.hash).toBe('#/admin/veterinarios');
    expect(menu()).toEqual(['Veterinários', 'Contas da equipe']);

    await clicar('Sair');
    expect(sair).toHaveBeenCalledTimes(1);
    expect(window.location.hash).toBe('#/login');
    expect(menu()).toEqual([]);
});

test('C21 conclusão do autocadastro abre painel do Tutor', async () => {
    await abrir(null);
    verificarCodigo.mockResolvedValue({ data: { emailVerificado: true } });
    concluirCadastroTutor.mockResolvedValue({ data: obterConta('TUTOR') });
    await clicar('Criar conta de tutor');
    await informarCodigo();

    expect(verificarCodigo).toHaveBeenCalledWith('ana@example.com', 'CADASTRO', '12345678');
    expect(concluirCadastroTutor).not.toHaveBeenCalled();
    await preencher('NOME COMPLETO *', 'Ana');
    await preencher('TELEFONE / WHATSAPP *', 'abc81999991234567890');
    expect(container.querySelector('input[type="tel"]').value).toBe('(81) 99999-1234');
    await preencher('TELEFONE / WHATSAPP *', '8133334444');
    expect(container.querySelector('input[type="tel"]').value).toBe('(81) 3333-4444');
    await preencher('CPF *', 'abc123456789091234567');
    expect(container.querySelector('input[placeholder="000.000.000-00"]').value).toBe('123.456.789-09');
    await preencher('TELEFONE / WHATSAPP *', '');
    await preencher('CPF *', '');
    expect(container.querySelector('input[type="tel"]').value).toBe('');
    expect(container.querySelector('input[placeholder="000.000.000-00"]').value).toBe('');
    await preencher('TELEFONE / WHATSAPP *', '11999990000');
    await preencher('CPF *', '12345678909');
    expect(container.querySelector('input[type="tel"]').value).toBe('(11) 99999-0000');
    expect(container.querySelector('input[placeholder="000.000.000-00"]').value).toBe('123.456.789-09');
    await preencher('NOME DO PET *', 'Rex');
    await clicar('Concluir Cadastro & Entrar');

    expect(concluirCadastroTutor).toHaveBeenCalledWith(expect.objectContaining({ email: 'ana@example.com',
        telefone: '11999990000', cpf: '12345678909',
        pets: [expect.objectContaining({ nome: 'Rex' })] }));
    expect(window.location.hash).toBe('#/tutor/inicio');
    expect(container.querySelector('main h1').textContent).toBe('Olá, Ana!');
});
