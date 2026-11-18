import React, { act } from "react";
import { createRoot } from "react-dom/client";
import PainelRecepcionista from "./PainelRecepcionista";
import { listarConsultasDoDia, obterPredicaoNoShow } from "../api/api";

jest.mock("../api/api", () => ({
  api: { patch: jest.fn() },
  listarConsultasDoDia: jest.fn(),
  obterPredicaoNoShow: jest.fn(),
  dispararLembrete: jest.fn(),
}));

const consulta = (id, petNome, status = "AGENDADA") => ({
  id, petNome, tutorNome: "Tutor " + id, veterinarioNome: "Dra. Ana",
  dataHora: "2026-11-17T09:00:00", status,
});

let container;
let root;
beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.resetAllMocks();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const flush = async () => {
  for (let i = 0; i < 10; i += 1) {
    // eslint-disable-next-line no-await-in-loop
    await act(async () => { await Promise.resolve(); });
  }
};

const linhaDo = (petNome) =>
  [...container.querySelectorAll("tbody tr")].find(tr => tr.textContent.includes(petNome));

test("busca a predicao de cada consulta ativa e usa a categoria do back-end", async () => {
  listarConsultasDoDia.mockResolvedValue({ data: [consulta(1, "Rex"), consulta(2, "Mel", "CONFIRMADA")] });
  obterPredicaoNoShow.mockImplementation(async (id) => ({
    data: id === 1
      ? { score: 0.65, categoria: "ALTO", motivo: "4 faltas em 6 consultas" }
      : { score: 0.3, categoria: "MEDIO", motivo: "1 falta" },
  }));

  await act(async () => { root.render(<PainelRecepcionista />); });
  await flush();

  expect(obterPredicaoNoShow).toHaveBeenCalledWith(1);
  expect(obterPredicaoNoShow).toHaveBeenCalledWith(2);
  // 0.65 e ALTO para o modelo; o front nao pode reclassificar como "Médio".
  expect(linhaDo("Rex").textContent).toContain("Alto (65%)");
  expect(linhaDo("Mel").textContent).toContain("Médio (30%)");
});

test("score indisponivel nunca aparece como baixo risco", async () => {
  listarConsultasDoDia.mockResolvedValue({ data: [consulta(1, "Rex"), consulta(2, "Mel")] });
  obterPredicaoNoShow.mockImplementation(async (id) => {
    if (id === 1) {
      return { data: { score: null, categoria: "INDISPONIVEL", motivo: "Score indisponivel no momento" } };
    }
    throw new Error("Network Error");
  });

  await act(async () => { root.render(<PainelRecepcionista />); });
  await flush();

  for (const pet of ["Rex", "Mel"]) {
    expect(linhaDo(pet).textContent).toContain("Indisponível");
    expect(linhaDo(pet).textContent).not.toContain("Baixo");
  }
});

test("nao pede score para consultas que ja terminaram", async () => {
  listarConsultasDoDia.mockResolvedValue({
    data: [consulta(1, "Rex", "REALIZADA"), consulta(2, "Mel", "CANCELADA")],
  });

  await act(async () => { root.render(<PainelRecepcionista />); });
  await flush();

  expect(obterPredicaoNoShow).not.toHaveBeenCalled();
});
