import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import CadastroTutorPet from "./CadastroTutorPet";
import AgendamentoForm from "./AgendamentoForm";
import CadastroVeterinario from "./CadastroVeterinario";
import Veterinarios from "./Veterinarios";
import App from "../App";
import { cadastrarTutor, listarPets, listarVeterinarios, listarDisponibilidade, criarConsulta, cadastrarVeterinario,
  obterVeterinario, listarHorariosVeterinario, atualizarVeterinario, atualizarHorariosVeterinario } from "../api/api";

jest.mock("../api/api", () => ({
  cadastrarTutor: jest.fn(), listarPets: jest.fn(), listarVeterinarios: jest.fn(),
  listarDisponibilidade: jest.fn(), criarConsulta: jest.fn(),
  cadastrarVeterinario: jest.fn(),
  obterVeterinario: jest.fn(), listarHorariosVeterinario: jest.fn(),
  atualizarVeterinario: jest.fn(), atualizarHorariosVeterinario: jest.fn(),
}));

let container;
let root;
beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.resetAllMocks();
  listarPets.mockResolvedValue({ data: [{ id: 12, nome: "Luna", especie: "Gato", tutorNome: "Ana" }] });
  listarVeterinarios.mockResolvedValue({ data: [{ id: 3, nome: "Maria" }] });
  listarDisponibilidade.mockImplementation((...argumentos) => Promise.resolve({
    data: argumentos[1] === "2099-12-10"
      ? [{ horario: "2099-12-10T09:00:00", disponivel: true }]
      : [],
  }));
  criarConsulta.mockResolvedValue({ data: {} });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const preencher = async (seletor, valor) => {
  const campo = container.querySelector(seletor);
  await act(async () => Simulate.change(campo, { target: { value: valor } }));
};
const enviar = async () => {
  await act(async () => Simulate.submit(container.querySelector("form")));
};

test("cadastro rejeita dados invalidos e mostra erros junto aos campos", async () => {
  act(() => root.render(<CadastroTutorPet />));
  await preencher('input[type="email"]', "invalido");
  await preencher('input[placeholder="000.000.000-00"]', "11111111111");
  await enviar();
  expect(cadastrarTutor).not.toHaveBeenCalled();
  expect(container.querySelector('input[type="email"]').getAttribute("aria-invalid")).toBe("true");
  expect(container.textContent).toContain("Informe um CPF valido.");
});

test("cadastro formata numeros e envia dados normalizados preservando idade zero", async () => {
  cadastrarTutor.mockResolvedValue({ data: {} });
  act(() => root.render(<CadastroTutorPet />));
  await preencher('input[placeholder="Ex: Carlos Silva"]', " Ana ");
  await preencher('input[type="email"]', " ana@exemplo.com ");
  await preencher('input[type="tel"]', "11999998888");
  await preencher('input[placeholder="000.000.000-00"]', "52998224725");
  await preencher('input[placeholder="Ex: Thor"]', " Luna ");
  await preencher('input[type="number"]', "0");
  expect(container.querySelector('input[type="tel"]').value).toBe("(11) 99999-8888");
  expect(container.querySelector('input[placeholder="000.000.000-00"]').value).toBe("529.982.247-25");
  await enviar();
  expect(cadastrarTutor).toHaveBeenCalledWith({
    nome: "Ana", email: "ana@exemplo.com", cpf: "52998224725", telefone: "11999998888",
    pets: [{ nome: "Luna", especie: "", idade: 0, alergiasCriticas: "" }],
  });
  expect(container.textContent).toContain("cadastrados com sucesso");
});

const abrirAgendamento = async () => {
  await act(async () => root.render(<AgendamentoForm />));
};
const buscarHorarios = async () => {
  await preencher("select:nth-of-type(1)", "12");
  const veterinario = container.querySelectorAll("select")[1];
  await act(async () => Simulate.change(veterinario, { target: { value: "3" } }));
  await preencher('input[type="date"]', "2099-12-10");
  await act(async () => {});
};

test("agendamento mostra nomes e envia apenas o id do pet escolhido", async () => {
  await abrirAgendamento();
  expect(container.textContent).toContain("Luna - Gato (Tutor: Ana)");
  expect(container.querySelector('input[type="number"]')).toBeNull();
  await buscarHorarios();
  act(() => Simulate.click(container.querySelector("button.horario")));
  await enviar();
  expect(criarConsulta).toHaveBeenCalledWith({ petId: 12, veterinarioId: 3, dataHora: "2099-12-10T09:00:00" });
});

test("trocar data ou veterinario invalida o horario anterior", async () => {
  await abrirAgendamento();
  await buscarHorarios();
  act(() => Simulate.click(container.querySelector("button.horario")));
  await preencher('input[type="date"]', "2099-12-11");
  expect(container.querySelector("button.horario")).toBeNull();
  expect(container.querySelector('button[type="submit"]').disabled).toBe(true);
  await enviar();
  expect(criarConsulta).not.toHaveBeenCalled();
});

test("resposta atrasada de disponibilidade nao restaura horarios antigos", async () => {
  let responder;
  listarDisponibilidade.mockImplementationOnce(() => new Promise((resolve) => { responder = resolve; }));
  await abrirAgendamento();
  await buscarHorarios();
  await preencher('input[type="date"]', "2099-12-11");
  await act(async () => responder({ data: [{ horario: "2099-12-10T09:00:00", disponivel: true }] }));
  expect(container.querySelector("button.horario")).toBeNull();
});

test("lista vazia e erro de pets impedem agendamento e permitem tentar novamente", async () => {
  listarPets.mockRejectedValueOnce(new Error("Offline"));
  await abrirAgendamento();
  expect(container.textContent).toContain("Nao foi possivel carregar os pets.");
  expect(container.querySelector("select").disabled).toBe(true);
  listarPets.mockResolvedValueOnce({ data: [] });
  await act(async () => Simulate.click(container.querySelector("button.btn-secundario")));
  expect(container.textContent).toContain("Nenhum pet cadastrado.");
  expect(container.querySelector('button[type="submit"]').disabled).toBe(true);
});

test("agendamento encaminha o tutor recebido para a listagem de pets", async () => {
  await act(async () => root.render(<AgendamentoForm tutorId={7} />));
  expect(listarPets).toHaveBeenCalledWith(7);
});

const abrirVeterinario = () => act(() => root.render(<CadastroVeterinario />));
const segunda = () => container.querySelector('input[type="checkbox"]');
const selecionarSegunda = () => act(() => Simulate.change(segunda(), { target: { checked: true } }));

test("veterinario exige nome e ao menos um dia antes de chamar a API", async () => {
  abrirVeterinario();
  await enviar();
  expect(cadastrarVeterinario).not.toHaveBeenCalled();
  expect(container.textContent).toContain("Selecione pelo menos um dia");
  expect(container.querySelector('input[name="nome"]').getAttribute("aria-invalid")).toBe("true");
});

test("veterinario envia contrato semanal sem ids locais e limpa apenas apos sucesso", async () => {
  cadastrarVeterinario.mockResolvedValue({ data: { id: 8 } });
  abrirVeterinario();
  await preencher('input[name="nome"]', " Ana Souza ");
  await preencher('input[name="especialidade"]', " Clinica geral ");
  selecionarSegunda();
  act(() => Simulate.click(container.querySelector('.veterinario-add-period')));
  await enviar();
  expect(cadastrarVeterinario).toHaveBeenCalledWith({
    nome: "Ana Souza", especialidade: "Clinica geral", horarios: [
      { diaSemana: "MONDAY", horaInicio: "08:00", horaFim: "12:00" },
      { diaSemana: "MONDAY", horaInicio: "14:00", horaFim: "18:00" },
    ],
  });
  expect(container.querySelector('[role="status"]').textContent).toContain("Ana Souza");
  expect(container.querySelector('input[name="nome"]').value).toBe("");
  expect(segunda().checked).toBe(false);
});

test("veterinario rejeita sobreposicao e horarios fora da grade", async () => {
  abrirVeterinario();
  await preencher('input[name="nome"]', "Ana");
  selecionarSegunda();
  act(() => Simulate.click(container.querySelector('.veterinario-add-period')));
  await preencher('input[aria-label="Segunda-feira, início do período 2"]', "11:30");
  await enviar();
  expect(cadastrarVeterinario).not.toHaveBeenCalled();
  expect(container.textContent).toContain("se sobrepõe");
  await preencher('input[aria-label="Segunda-feira, início do período 2"]', "14:15");
  await enviar();
  expect(cadastrarVeterinario).not.toHaveBeenCalled();
  expect(container.textContent).toContain("intervalos de 30 minutos");
});

test("veterinario preserva dados e apresenta a mensagem do backend em caso de falha", async () => {
  cadastrarVeterinario.mockRejectedValue({ response: { data: { mensagem: "Conflito no cadastro." } } });
  abrirVeterinario();
  await preencher('input[name="nome"]', "Ana");
  selecionarSegunda();
  await enviar();
  expect(container.querySelector('[role="alert"]').textContent).toBe("Conflito no cadastro.");
  expect(container.querySelector('input[name="nome"]').value).toBe("Ana");
  expect(segunda().checked).toBe(true);
});

test("veterinario remove periodos e desmarcar um dia nao envia seus horarios", async () => {
  cadastrarVeterinario.mockResolvedValue({ data: {} });
  abrirVeterinario();
  await preencher('input[name="nome"]', "Ana");
  selecionarSegunda();
  act(() => Simulate.click(container.querySelector('.veterinario-add-period')));
  act(() => Simulate.click(container.querySelector('.veterinario-remove-period')));
  expect(container.querySelector('input[type="time"]').value).toBe("14:00");
  act(() => Simulate.change(segunda(), { target: { checked: false } }));
  const quarta = container.querySelectorAll('input[type="checkbox"]')[2];
  act(() => Simulate.change(quarta, { target: { checked: true } }));
  await enviar();
  expect(cadastrarVeterinario).toHaveBeenCalledWith({ nome: "Ana", especialidade: "", horarios: [
    { diaSemana: "WEDNESDAY", horaInicio: "08:00", horaFim: "12:00" },
  ] });
});

test("veterinario impede fim anterior ao inicio e aceita periodos contiguos", async () => {
  cadastrarVeterinario.mockResolvedValue({ data: {} });
  abrirVeterinario();
  await preencher('input[name="nome"]', "Ana");
  selecionarSegunda();
  await preencher('input[aria-label="Segunda-feira, fim do período 1"]', "07:30");
  await enviar();
  expect(cadastrarVeterinario).not.toHaveBeenCalled();
  expect(container.textContent).toContain("O fim deve ser depois");
  await preencher('input[aria-label="Segunda-feira, fim do período 1"]', "14:00");
  act(() => Simulate.click(container.querySelector('.veterinario-add-period')));
  await enviar();
  expect(cadastrarVeterinario).toHaveBeenCalledTimes(1);
});

test("veterinario bloqueia envios duplicados enquanto aguarda a API", async () => {
  let concluir;
  cadastrarVeterinario.mockImplementation(() => new Promise(resolve => { concluir = resolve; }));
  abrirVeterinario();
  await preencher('input[name="nome"]', "Ana");
  selecionarSegunda();
  await act(async () => {
    Simulate.submit(container.querySelector('form'));
    Simulate.submit(container.querySelector('form'));
  });
  expect(cadastrarVeterinario).toHaveBeenCalledTimes(1);
  expect(container.querySelector('fieldset').disabled).toBe(true);
  expect(container.querySelector('button[type="submit"]').textContent).toContain("Cadastrando");
  await act(async () => concluir({ data: {} }));
  expect(container.querySelector('fieldset').disabled).toBe(false);
});

test("navegacao abre a tela de veterinarios", () => {
  act(() => root.render(<App />));
  const aba = [...container.querySelectorAll('.navbar-tab')].find(botao => botao.textContent === "Veterinários");
  act(() => Simulate.click(aba));
  expect(container.querySelector('h1').textContent).toBe("Cadastro de Veterinários");
  expect(aba.getAttribute('aria-current')).toBe('page');
});

test("veterinario permite tentar novamente apos falha de conexao", async () => {
  cadastrarVeterinario.mockRejectedValueOnce(new Error("Offline")).mockResolvedValueOnce({ data: {} });
  abrirVeterinario();
  await preencher('input[name="nome"]', "Ana");
  selecionarSegunda();
  await enviar();
  expect(container.querySelector('[role="alert"]').textContent).toContain("Não foi possível cadastrar");
  expect(container.querySelector('fieldset').disabled).toBe(false);
  await enviar();
  expect(cadastrarVeterinario).toHaveBeenCalledTimes(2);
  expect(container.querySelector('[role="alert"]')).toBeNull();
  expect(container.querySelector('[role="status"]').textContent).toContain("sucesso");
});

const abrirEdicaoVeterinario = async () => {
  obterVeterinario.mockResolvedValue({ data: { id: 3, nome: 'Maria', especialidade: 'Geral', ativo: true } });
  listarHorariosVeterinario.mockResolvedValue({ data: [
    { diaSemana: 'MONDAY', horaInicio: '08:00:00', horaFim: '12:00:00' },
  ] });
  await act(async () => root.render(<CadastroVeterinario veterinarioId={3} />));
};
const salvarHorarios = () => act(async () => Simulate.submit(container.querySelector('form[aria-label="Atualizar horários"]')));

test("edicao carrega dados e escala e salva cada recurso separadamente", async () => {
  atualizarVeterinario.mockResolvedValue({ data: { id: 3, nome: 'Maria Silva', ativo: true } });
  atualizarHorariosVeterinario.mockResolvedValue({ data: [] });
  await abrirEdicaoVeterinario();
  expect(container.querySelector('input[type="time"]').value).toBe('08:00');
  await preencher('input[name="nome"]', ' Maria Silva ');
  await enviar();
  expect(atualizarVeterinario).toHaveBeenCalledWith(3, { nome: 'Maria Silva', especialidade: 'Geral', ativo: true });
  expect(atualizarHorariosVeterinario).not.toHaveBeenCalled();
  await preencher('input[aria-label="Segunda-feira, fim do período 1"]', '11:00');
  await salvarHorarios();
  expect(atualizarHorariosVeterinario).toHaveBeenCalledWith(3, { horarios: [
    { diaSemana: 'MONDAY', horaInicio: '08:00', horaFim: '11:00' },
  ] });
  expect(cadastrarVeterinario).not.toHaveBeenCalled();
});

test("edicao permite esvaziar escala e preserva dados quando backend retorna conflito", async () => {
  atualizarHorariosVeterinario.mockRejectedValue({ response: { data: { mensagem: 'A nova escala deixa consultas futuras fora do atendimento.' } } });
  await abrirEdicaoVeterinario();
  const dia = container.querySelector('.veterinario-week input[type="checkbox"]');
  act(() => Simulate.change(dia, { target: { checked: false } }));
  await salvarHorarios();
  expect(atualizarHorariosVeterinario).toHaveBeenCalledWith(3, { horarios: [] });
  expect(container.querySelector('[role="alert"]').textContent).toContain('consultas futuras');
  expect(container.querySelector('input[name="nome"]').value).toBe('Maria');
  expect(dia.checked).toBe(false);
});

test("tela permite selecionar ativos e inativos e atualiza o nome na lista apos salvar", async () => {
  listarVeterinarios.mockImplementation(params => Promise.resolve({ data: params.ativo
    ? [{ id: 3, nome: 'Maria', especialidade: 'Geral', ativo: true }]
    : [{ id: 4, nome: 'Ana', especialidade: 'Geral', ativo: false }] }));
  obterVeterinario.mockResolvedValue({ data: { id: 3, nome: 'Maria', especialidade: 'Geral', ativo: true } });
  listarHorariosVeterinario.mockResolvedValue({ data: [] });
  atualizarVeterinario.mockResolvedValue({ data: { id: 3, nome: 'Maria Silva', especialidade: 'Geral', ativo: true } });
  await act(async () => root.render(<Veterinarios />));
  await act(async () => Simulate.click([...container.querySelectorAll('button')].find(botao => botao.textContent === 'Atualizar')));
  expect(listarVeterinarios).toHaveBeenCalledWith({ ativo: true });
  expect(listarVeterinarios).toHaveBeenCalledWith({ ativo: false });
  expect(container.querySelector('select').textContent).toContain('Ana - Geral (Inativo)');
  await preencher('select', '3');
  await preencher('input[name="nome"]', 'Maria Silva');
  await enviar();
  expect(container.querySelector('select').textContent).toContain('Maria Silva');
  expect(container.querySelector('select').value).toBe('3');
});

test("edicao bloqueia resposta atrasada de outro veterinario", async () => {
  let responderAntigo;
  obterVeterinario.mockImplementation(id => id === 3
    ? new Promise(resolve => { responderAntigo = resolve; })
    : Promise.resolve({ data: { id: 4, nome: 'Ana', ativo: false } }));
  listarHorariosVeterinario.mockResolvedValue({ data: [] });
  await act(async () => root.render(<CadastroVeterinario veterinarioId={3} />));
  expect(container.textContent).toContain('Carregando veterinário');
  await act(async () => root.render(<CadastroVeterinario veterinarioId={4} />));
  expect(container.querySelector('input[name="nome"]').value).toBe('Ana');
  await act(async () => responderAntigo({ data: { id: 3, nome: 'Maria', ativo: true } }));
  expect(container.querySelector('input[name="nome"]').value).toBe('Ana');
});

test("edicao permite tentar carregar novamente sem mostrar formulario incompleto", async () => {
  obterVeterinario.mockRejectedValueOnce(new Error('Offline'))
    .mockResolvedValueOnce({ data: { id: 3, nome: 'Maria', ativo: true } });
  listarHorariosVeterinario.mockResolvedValue({ data: [] });
  await act(async () => root.render(<CadastroVeterinario veterinarioId={3} />));
  expect(container.querySelector('form')).toBeNull();
  expect(container.querySelector('[role="alert"]').textContent).toContain('Não foi possível carregar');
  await act(async () => Simulate.click(container.querySelector('button')));
  expect(container.querySelector('input[name="nome"]').value).toBe('Maria');
});

test("dados podem ser salvos sem enviar uma escala editada invalida", async () => {
  atualizarVeterinario.mockResolvedValue({ data: { id: 3, nome: 'Maria', especialidade: 'Geral', ativo: true } });
  await abrirEdicaoVeterinario();
  await preencher('input[aria-label="Segunda-feira, início do período 1"]', '08:15');
  await enviar();
  expect(atualizarVeterinario).toHaveBeenCalledTimes(1);
  expect(atualizarHorariosVeterinario).not.toHaveBeenCalled();
  expect(container.querySelector('input[type="time"]').value).toBe('08:15');
  await salvarHorarios();
  expect(atualizarHorariosVeterinario).not.toHaveBeenCalled();
  expect(container.textContent).toContain('intervalos de 30 minutos');
});

test("edicao reativa veterinario inativo sem modificar os horarios", async () => {
  obterVeterinario.mockResolvedValue({ data: { id: 4, nome: 'Ana', especialidade: null, ativo: false } });
  listarHorariosVeterinario.mockResolvedValue({ data: [] });
  atualizarVeterinario.mockResolvedValue({ data: { id: 4, nome: 'Ana', especialidade: '', ativo: true } });
  await act(async () => root.render(<CadastroVeterinario veterinarioId={4} />));
  const ativo = container.querySelector('input[type="checkbox"]');
  expect(ativo.checked).toBe(false);
  act(() => Simulate.change(ativo, { target: { checked: true } }));
  await enviar();
  expect(atualizarVeterinario).toHaveBeenCalledWith(4, { nome: 'Ana', especialidade: '', ativo: true });
  expect(atualizarHorariosVeterinario).not.toHaveBeenCalled();
});

test("falha na listagem permite nova tentativa e lista vazia nao abre edicao", async () => {
  listarVeterinarios.mockRejectedValueOnce(new Error('Offline'));
  await act(async () => root.render(<Veterinarios />));
  await act(async () => Simulate.click([...container.querySelectorAll('button')].find(botao => botao.textContent === 'Atualizar')));
  expect(container.querySelector('[role="alert"]').textContent).toContain('Não foi possível carregar');
  listarVeterinarios.mockResolvedValue({ data: [] });
  await act(async () => Simulate.click([...container.querySelectorAll('button')].find(botao => botao.textContent === 'Tentar novamente')));
  expect(container.textContent).toContain('Nenhum veterinário cadastrado.');
  expect(container.querySelector('form')).toBeNull();
});

test("salvamento de edicao bloqueia selecao e modo ate a API responder", async () => {
  let concluir;
  listarVeterinarios.mockResolvedValue({ data: [{ id: 3, nome: 'Maria', ativo: true }] });
  obterVeterinario.mockResolvedValue({ data: { id: 3, nome: 'Maria', especialidade: '', ativo: true } });
  listarHorariosVeterinario.mockResolvedValue({ data: [] });
  atualizarVeterinario.mockImplementation(() => new Promise(resolve => { concluir = resolve; }));
  await act(async () => root.render(<Veterinarios />));
  await act(async () => Simulate.click([...container.querySelectorAll('button')].find(botao => botao.textContent === 'Atualizar')));
  await preencher('select', '3');
  await enviar();
  expect(container.querySelector('select').disabled).toBe(true);
  expect(container.querySelector('.veterinario-mode button').disabled).toBe(true);
  await enviar();
  expect(atualizarVeterinario).toHaveBeenCalledTimes(1);
  await act(async () => concluir({ data: { id: 3, nome: 'Maria', ativo: true } }));
  expect(container.querySelector('select').disabled).toBe(false);
});