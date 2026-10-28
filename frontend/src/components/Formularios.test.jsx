import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import CadastroTutorPet from "./CadastroTutorPet";
import AgendamentoForm from "./AgendamentoForm";
import { cadastrarTutor, listarPets, listarVeterinarios, listarDisponibilidade, criarConsulta } from "../api/api";

jest.mock("../api/api", () => ({
  cadastrarTutor: jest.fn(), listarPets: jest.fn(), listarVeterinarios: jest.fn(),
  listarDisponibilidade: jest.fn(), criarConsulta: jest.fn(),
}));

let container;
let root;
beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.resetAllMocks();
  listarPets.mockResolvedValue({ data: [{ id: 12, nome: "Luna", especie: "Gato", tutorNome: "Ana" }] });
  listarVeterinarios.mockResolvedValue({ data: [{ id: 3, nome: "Maria" }] });
  listarDisponibilidade.mockResolvedValue({ data: [{ horario: "2099-12-10T09:00:00", disponivel: true }] });
  criarConsulta.mockResolvedValue({ data: {} });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const preencher = (seletor, valor) => {
  const campo = container.querySelector(seletor);
  act(() => Simulate.change(campo, { target: { value: valor } }));
};
const enviar = async () => {
  await act(async () => Simulate.submit(container.querySelector("form")));
};

test("cadastro rejeita dados invalidos e mostra erros junto aos campos", async () => {
  act(() => root.render(<CadastroTutorPet />));
  preencher('input[type="email"]', "invalido");
  preencher('input[placeholder="000.000.000-00"]', "11111111111");
  await enviar();
  expect(cadastrarTutor).not.toHaveBeenCalled();
  expect(container.querySelector('input[type="email"]').getAttribute("aria-invalid")).toBe("true");
  expect(container.textContent).toContain("Informe um CPF valido.");
});

test("cadastro formata numeros e envia dados normalizados preservando idade zero", async () => {
  cadastrarTutor.mockResolvedValue({ data: {} });
  act(() => root.render(<CadastroTutorPet />));
  preencher("fieldset input", " Ana ");
  preencher('input[type="email"]', " ana@exemplo.com ");
  preencher('input[type="tel"]', "11999998888");
  preencher('input[placeholder="000.000.000-00"]', "52998224725");
  preencher("fieldset:nth-of-type(2) input", " Luna ");
  preencher('input[type="number"]', "0");
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
  preencher("select:nth-of-type(1)", "12");
  const veterinario = container.querySelectorAll("select")[1];
  act(() => Simulate.change(veterinario, { target: { value: "3" } }));
  preencher('input[type="date"]', "2099-12-10");
  await act(async () => Simulate.click(container.querySelector("button.btn-secundario")));
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
  preencher('input[type="date"]', "2099-12-11");
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
  preencher('input[type="date"]', "2099-12-11");
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