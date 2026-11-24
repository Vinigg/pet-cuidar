import React, { act } from "react";
import { createRoot } from "react-dom/client";
import PainelRecepcionista from "./PainelRecepcionista";
import { api, listarConsultasDoDia, obterPredicaoNoShow } from "../api/api";
import { Simulate } from "react-dom/test-utils";

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

test("agenda vazia e falha de rede nao exibem pacientes simulados", async () => {
  listarConsultasDoDia.mockResolvedValueOnce({ data: [] });
  await act(async () => { root.render(<PainelRecepcionista />); });
  await flush();
  expect(container.querySelectorAll("tbody tr")).toHaveLength(0);
  expect(container.textContent).toContain("Nenhuma consulta encontrada");
  listarConsultasDoDia.mockRejectedValueOnce(new Error("Offline"));
  const sincronizar = [...container.querySelectorAll("button")].find(b => b.textContent.includes("Sincronizar"));
  await act(async () => sincronizar.click());
  await flush();
  expect(container.querySelector('[role="alert"]').textContent).toContain("Não foi possível carregar");
  expect(container.textContent).not.toContain("Thor");
  expect(container.querySelectorAll("tbody tr")).toHaveLength(0);
});

test("mudanca de status rejeitada permanece no modal sem alterar consulta localmente", async () => {
  listarConsultasDoDia.mockResolvedValue({ data: [consulta(1, "Rex")] });
  obterPredicaoNoShow.mockResolvedValue({ data: { score: null, categoria: "INDISPONIVEL" } });
  api.patch.mockRejectedValue({ response: { data: { mensagem: "Transicao nao permitida." } } });
  await act(async () => { root.render(<PainelRecepcionista />); });
  await flush();
  await act(async () => [...container.querySelectorAll("button")].find(b => b.textContent === "Atualizar").click());
  expect(container.querySelector('input[value="EM_ATENDIMENTO"]')).toBeNull();
  const cancelada = container.querySelector('input[value="CANCELADA"]');
  await act(async () => Simulate.change(cancelada));
  await act(async () => [...container.querySelectorAll("button")].find(b => b.textContent === "Salvar alteração").click());
  expect(api.patch).toHaveBeenCalledWith("/consultas/1/status", { status: "CANCELADA" });
  expect(container.querySelector('[role="dialog"]')).not.toBeNull();
  expect(container.querySelector('[role="alert"]').textContent).toContain("Transicao nao permitida.");
  expect(linhaDo("Rex").textContent).toContain("Agendada");
  expect(listarConsultasDoDia).toHaveBeenCalledTimes(1);
});

test("resposta de agenda anterior nao substitui a data selecionada", async () => {
  let responderAnterior;
  listarConsultasDoDia.mockImplementationOnce(() => new Promise(resolve => { responderAnterior = resolve; }));
  listarConsultasDoDia.mockResolvedValueOnce({ data: [consulta(2, "Data nova", "REALIZADA")] });
  await act(async () => { root.render(<PainelRecepcionista />); });
  const data = container.querySelector('input[type="date"]');
  await act(async () => {
    data.value = "2026-12-01";
    Simulate.change(data);
  });
  await flush();
  await act(async () => responderAnterior({ data: [consulta(1, "Data antiga")] }));
  await flush();
  expect(container.textContent).toContain("Data nova");
  expect(container.textContent).not.toContain("Data antiga");
  expect(obterPredicaoNoShow).not.toHaveBeenCalled();
});
