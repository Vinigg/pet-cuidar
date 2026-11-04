import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import HistoricoPet from "./HistoricoPet";
import { listarPets, obterHistoricoPet, obterResumoHistoricoPet } from "../api/api";

jest.mock("../api/api", () => ({
  listarPets: jest.fn(), obterHistoricoPet: jest.fn(), obterResumoHistoricoPet: jest.fn(),
}));

const historicoBob = {
  petId: 2, petNome: "Bob", especie: "Pitbull", idade: 4, tutorNome: "João",
  itensCriticos: "Alergia a dipirona",
  consultas: [
    {
      consultaId: 10, dataHora: "2026-11-04T17:30:00", status: "REALIZADA",
      veterinarioId: 3, veterinarioNome: "Alberto Siqueira",
      atendimentos: [{ id: 100, descricaoClinica: "Otite leve, prescrito limpeza auricular.",
        veterinarioNome: "Alberto Siqueira", dataCriacao: "2026-11-04T17:45:00", dataAtualizacao: "2026-11-04T17:45:00" }],
    },
    {
      consultaId: 9, dataHora: "2026-10-01T09:00:00", status: "CANCELADA",
      veterinarioId: 1, veterinarioNome: "Dr. Bruno Salgado", atendimentos: [],
    },
  ],
  atendimentosSemConsulta: [],
};

let container;
let root;
beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.resetAllMocks();
  listarPets.mockResolvedValue({ data: [{ id: 2, nome: "Bob", especie: "Pitbull", tutorNome: "João" }] });
  obterHistoricoPet.mockResolvedValue({ data: historicoBob });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function renderizarESelecionarBob() {
  await act(async () => root.render(<HistoricoPet />));
  const select = container.querySelector("#historico-pet");
  expect(select.textContent).toContain("Bob (Pitbull) - Tutor: João");
  await act(async () => {
    select.value = "2";
    Simulate.change(select);
  });
}

test("exibe consultas do pet com atendimentos, itens críticos e resumo identificado como IA", async () => {
  obterResumoHistoricoPet.mockResolvedValue({
    data: { resumo: "Bob teve otite recente.", itensCriticos: "Alergia a dipirona", usouFallback: false },
  });

  await renderizarESelecionarBob();

  expect(obterHistoricoPet).toHaveBeenCalledWith("2");
  expect(container.textContent).toContain("Alergia a dipirona");
  expect(container.textContent).toContain("Dado do cadastro");
  expect(container.textContent).toContain("Texto gerado por IA");
  expect(container.textContent).toContain("Bob teve otite recente.");
  expect(container.textContent).toContain("Alberto Siqueira");
  expect(container.textContent).toContain("Otite leve, prescrito limpeza auricular.");
  expect(container.textContent).toContain("Nenhum atendimento clínico registrado nesta consulta.");
  expect(container.querySelectorAll(".timeline-item")).toHaveLength(2);
});

test("falha da IA não impede acesso aos registros do histórico", async () => {
  obterResumoHistoricoPet.mockRejectedValue(new Error("IA fora do ar"));
  jest.spyOn(console, "error").mockImplementation(() => {});

  await renderizarESelecionarBob();

  expect(container.textContent).toContain("Resumo clínico indisponível");
  expect(container.textContent).toContain("Otite leve, prescrito limpeza auricular.");
  expect(container.textContent).toContain("Alergia a dipirona");
  console.error.mockRestore();
});

test("filtra consultas por status", async () => {
  obterResumoHistoricoPet.mockResolvedValue({ data: { resumo: "x", usouFallback: true } });

  await renderizarESelecionarBob();
  const filtro = container.querySelector("#filtro-status");
  await act(async () => {
    filtro.value = "CANCELADA";
    Simulate.change(filtro);
  });

  expect(container.querySelectorAll(".timeline-item")).toHaveLength(1);
  expect(container.textContent).toContain("Dr. Bruno Salgado");
  expect(container.textContent).not.toContain("Otite leve");
});
