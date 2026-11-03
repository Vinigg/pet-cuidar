import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import DetalhesConsulta from "./DetalhesConsulta";
import PainelRecepcionista from "./PainelRecepcionista";
import { obterConsulta, listarConsultasDoDia, obterPredicaoNoShow, dispararLembrete } from "../api/api";

jest.mock("../api/api", () => ({
  obterConsulta: jest.fn(),
  listarConsultasDoDia: jest.fn(),
  obterPredicaoNoShow: jest.fn(),
  dispararLembrete: jest.fn(),
}));

const DETALHE = {
  id: 42,
  dataHora: "2099-12-10T14:30:00",
  status: "CONFIRMADA",
  pet: { id: 7, nome: "Rex", especie: "Cachorro", idade: 4, alergiasCriticas: "Dipirona" },
  tutor: { id: 3, nome: "Ana Souza", email: "ana@example.com", telefone: "11999998888" },
  veterinario: { id: 1, nome: "Dr. Bruno Salgado", especialidade: "Clinica geral" },
};

const LISTA = [
  { id: 41, petNome: "Mia", tutorNome: "Carlos", veterinarioNome: "Dr. Bruno Salgado", dataHora: "2099-12-10T09:00:00", status: "AGENDADA" },
  { id: 42, petNome: "Rex", tutorNome: "Ana Souza", veterinarioNome: "Dr. Bruno Salgado", dataHora: "2099-12-10T14:30:00", status: "CONFIRMADA" },
];

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

const pendente = () => {
  let resolver;
  let rejeitar;
  const promessa = new Promise((res, rej) => {
    resolver = res;
    rejeitar = rej;
  });
  return { promessa, resolver, rejeitar };
};

const texto = () => container.textContent;

describe("DetalhesConsulta", () => {
  test("mostra carregamento e depois os dados retornados pela API, em secoes", async () => {
    const req = pendente();
    obterConsulta.mockReturnValue(req.promessa);
    await act(async () => root.render(<DetalhesConsulta consultaId={42} onFechar={() => {}} />));

    expect(obterConsulta).toHaveBeenCalledTimes(1);
    expect(obterConsulta).toHaveBeenCalledWith(42);
    expect(texto()).toContain("Carregando detalhes...");

    await act(async () => req.resolver({ data: DETALHE }));

    expect(texto()).not.toContain("Carregando");
    const titulos = [...container.querySelectorAll(".detalhe-secao h3")].map((h) => h.textContent);
    expect(titulos).toEqual(["Consulta", "Pet", "Tutor", "Veterinario"]);
    ["#42", "10/12/2099", "14:30", "Confirmada", "Rex", "Cachorro", "4 anos", "Dipirona",
      "Ana Souza", "ana@example.com", "11999998888", "Dr. Bruno Salgado", "Clinica geral"].forEach((v) =>
      expect(texto()).toContain(v)
    );
    expect(container.querySelector(".badge.status-confirmada")).not.toBeNull();
    expect(container.querySelector('[role="dialog"]').getAttribute("aria-modal")).toBe("true");
  });

  test("campos opcionais ausentes aparecem como 'Nao informado'", async () => {
    obterConsulta.mockResolvedValue({
      data: { ...DETALHE, pet: { ...DETALHE.pet, especie: null, idade: null, alergiasCriticas: null },
        tutor: { ...DETALHE.tutor, telefone: null }, veterinario: { ...DETALHE.veterinario, especialidade: null } },
    });
    await act(async () => root.render(<DetalhesConsulta consultaId={42} onFechar={() => {}} />));
    expect(container.querySelectorAll(".nao-informado")).toHaveLength(5);
    expect(texto()).not.toContain("null");
  });

  test("consulta inexistente (404) mostra mensagem propria", async () => {
    obterConsulta.mockRejectedValue({ response: { status: 404 } });
    await act(async () => root.render(<DetalhesConsulta consultaId={999} onFechar={() => {}} />));
    expect(texto()).toContain("Consulta nao encontrada");
    expect(texto()).not.toContain("Tentar novamente");
  });

  test("falha de comunicacao mostra erro e permite tentar novamente", async () => {
    obterConsulta.mockRejectedValueOnce(new Error("Network Error"));
    await act(async () => root.render(<DetalhesConsulta consultaId={42} onFechar={() => {}} />));
    expect(texto()).toContain("Nao foi possivel carregar os detalhes");

    obterConsulta.mockResolvedValueOnce({ data: DETALHE });
    const tentar = [...container.querySelectorAll("button")].find((b) => b.textContent === "Tentar novamente");
    await act(async () => Simulate.click(tentar));
    expect(obterConsulta).toHaveBeenCalledTimes(2);
    expect(texto()).toContain("Ana Souza");
  });

  test("fecha pelo botao, pela tecla Esc e clicando fora", async () => {
    obterConsulta.mockResolvedValue({ data: DETALHE });
    const onFechar = jest.fn();
    await act(async () => root.render(<DetalhesConsulta consultaId={42} onFechar={onFechar} />));

    act(() => Simulate.click(container.querySelector(".btn-fechar")));
    act(() => document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" })));
    act(() => Simulate.click(container.querySelector(".modal-fundo")));
    act(() => Simulate.click(container.querySelector(".modal")));
    expect(onFechar).toHaveBeenCalledTimes(3);
  });

  test("resposta atrasada de outra consulta nao substitui a atual", async () => {
    const antiga = pendente();
    obterConsulta.mockReturnValueOnce(antiga.promessa).mockResolvedValueOnce({ data: DETALHE });
    await act(async () => root.render(<DetalhesConsulta consultaId={41} onFechar={() => {}} />));
    await act(async () => root.render(<DetalhesConsulta consultaId={42} onFechar={() => {}} />));
    await act(async () => antiga.resolver({ data: { ...DETALHE, id: 41, tutor: { ...DETALHE.tutor, nome: "Outro" } } }));
    expect(texto()).toContain("#42");
    expect(texto()).not.toContain("Outro");
  });
});

describe("PainelRecepcionista - acesso aos detalhes", () => {
  const abrirPainel = async () => {
    listarConsultasDoDia.mockResolvedValue({ data: LISTA });
    await act(async () => root.render(<PainelRecepcionista />));
  };
  const botoesDetalhe = () => [...container.querySelectorAll("button")].filter((b) => b.textContent === "Ver detalhes");

  test("cada consulta tem a acao 'Ver detalhes' e abre o modal buscando pelo id real", async () => {
    await abrirPainel();
    expect(botoesDetalhe()).toHaveLength(2);
    expect(container.querySelector('[role="dialog"]')).toBeNull();

    obterConsulta.mockResolvedValue({ data: DETALHE });
    await act(async () => Simulate.click(botoesDetalhe()[1]));

    expect(obterConsulta).toHaveBeenCalledTimes(1);
    expect(obterConsulta).toHaveBeenCalledWith(42);
    expect(listarConsultasDoDia).toHaveBeenCalledTimes(1);
    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    expect(texto()).toContain("ana@example.com");
  });

  test("fechar o modal volta para a listagem sem recarregar a agenda", async () => {
    await abrirPainel();
    obterConsulta.mockResolvedValue({ data: DETALHE });
    await act(async () => Simulate.click(botoesDetalhe()[0]));
    expect(obterConsulta).toHaveBeenCalledWith(41);

    act(() => Simulate.click(container.querySelector(".btn-fechar")));
    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(botoesDetalhe()).toHaveLength(2);
    expect(listarConsultasDoDia).toHaveBeenCalledTimes(1);
    expect(obterPredicaoNoShow).not.toHaveBeenCalled();
    expect(dispararLembrete).not.toHaveBeenCalled();
  });
});
