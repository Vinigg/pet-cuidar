import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import FormularioAtendimento from "./FormularioAtendimento";
import {
  registrarAtendimento, listarAtendimentosPorPet, listarPets, listarVeterinarios, listarConsultasDoDia,
} from "../api/api";

jest.mock("../api/api", () => ({
  registrarAtendimento: jest.fn(), atualizarAtendimento: jest.fn(), obterAtendimento: jest.fn(),
  listarAtendimentosPorPet: jest.fn(), listarPets: jest.fn(), listarVeterinarios: jest.fn(),
  listarConsultasDoDia: jest.fn(),
}));

let container;
let root;
beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.resetAllMocks();
  listarPets.mockResolvedValue({ data: [{ id: 1, nome: "Rex" }] });
  listarVeterinarios.mockResolvedValue({ data: [{ id: 3, nome: "Bia" }] });
  listarConsultasDoDia.mockResolvedValue({
    data: [{ id: 2, petId: 1, veterinarioId: 3, petNome: "Rex", dataHora: "2026-11-11T10:00:00", status: "CANCELADA" }],
  });
  listarAtendimentosPorPet.mockResolvedValue({ data: [] });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const selecionar = async (seletor, valor) => {
  await act(async () => Simulate.change(container.querySelector(seletor), { target: { value: valor } }));
};

const preencherERegistrar = async () => {
  await act(async () => root.render(<FormularioAtendimento />));
  await selecionar("#pet", "1");
  await selecionar("#veterinario", "3");
  await selecionar("#consulta", "2");
  await selecionar("textarea", "Otite leve, tratamento topico.");
  await act(async () => Simulate.submit(container.querySelector("form")));
};

test("registro recusado com 409 mostra a mensagem do backend", async () => {
  registrarAtendimento.mockRejectedValue({
    response: { status: 409, data: { status: 409, mensagem: "Nao e possivel registrar atendimento para consulta com status CANCELADA." } },
  });

  await preencherERegistrar();

  expect(registrarAtendimento).toHaveBeenCalledWith(
    expect.objectContaining({ petId: 1, consultaId: 2, veterinarioId: 3 }));
  expect(container.querySelector('[role="alert"]').textContent)
    .toContain("Nao e possivel registrar atendimento para consulta com status CANCELADA.");
});

test("erro sem mensagem do backend mostra texto padrao", async () => {
  registrarAtendimento.mockRejectedValue(new Error("Network Error"));

  await preencherERegistrar();

  expect(container.querySelector('[role="alert"]').textContent)
    .toContain("Erro ao registrar atendimento. Tente novamente.");
});

test("registro com sucesso recarrega as consultas para mostrar o novo status", async () => {
  registrarAtendimento.mockResolvedValue({ data: { id: 9 } });

  await preencherERegistrar();

  expect(listarConsultasDoDia).toHaveBeenCalledTimes(2);
  expect(container.querySelector('[role="alert"]')).toBeNull();
});
