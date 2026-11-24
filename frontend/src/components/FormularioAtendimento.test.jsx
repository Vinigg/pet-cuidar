import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import FormularioAtendimento from "./FormularioAtendimento";
import {
  atualizarAtendimento, registrarAtendimento, listarAtendimentosPorPet, listarPets, listarVeterinarios, listarConsultasDoDia,
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
  jest.useFakeTimers();
  jest.setSystemTime(new Date(2026, 10, 24, 23, 30));
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
  jest.useRealTimers();
});

const selecionar = async (seletor, valor) => {
  await act(async () => Simulate.change(container.querySelector(seletor), { target: { value: valor } }));
};

const preencherERegistrar = async () => {
  await act(async () => root.render(<FormularioAtendimento conta={{ veterinarioId: 3 }} />));
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

const abrirFormulario = async () => {
  await act(async () => root.render(<FormularioAtendimento conta={{ veterinarioId: 3 }} />));
};

const valoresDasOpcoes = (seletor) =>
  Array.from(container.querySelectorAll(seletor + " option"), (opcao) => opcao.value).filter(Boolean);

const consultaNaData = (id, data, campos = {}) => ({
  id, petId: 1, veterinarioId: 3, petNome: "Rex", dataHora: data + "T10:00:00",
  status: "AGENDADA", ...campos,
});

const aguardarResposta = () => {
  let resolver;
  const promessa = new Promise((resolve) => { resolver = resolve; });
  return { promessa, resolver };
};

test("C1 abre com a data local de hoje", async () => {
  await abrirFormulario();

  expect(container.querySelector("#data-consulta").value).toBe("2026-11-24");
  expect(listarConsultasDoDia).toHaveBeenCalledWith("2026-11-24");
});

test("C2 impede selecionar uma data futura", async () => {
  listarConsultasDoDia.mockResolvedValue({ data: [consultaNaData(2, "2026-11-24")] });
  await abrirFormulario();

  const campoData = container.querySelector("#data-consulta");
  expect(campoData.max).toBe("2026-11-24");
  await selecionar("#data-consulta", "2026-11-25");
  expect(campoData.value).toBe("2026-11-24");
  expect(valoresDasOpcoes("#consulta")).toEqual(["2"]);
  expect(listarConsultasDoDia).not.toHaveBeenCalledWith("2026-11-25");
});

test("C4 trocar o dia limpa a consulta e a descrição", async () => {
  listarConsultasDoDia.mockResolvedValueOnce({ data: [consultaNaData(2, "2026-11-24")] })
    .mockResolvedValueOnce({ data: [consultaNaData(7, "2026-11-23")] });
  await abrirFormulario();
  await selecionar("#consulta", "2");
  await selecionar("textarea", "Descrição digitada para a primeira consulta.");
  expect(container.querySelector("#consulta").value).toBe("2");

  await selecionar("#data-consulta", "2026-11-23");

  expect(container.querySelector("#consulta").value).toBe("");
  expect(container.querySelector("textarea").value).toBe("");
  expect(valoresDasOpcoes("#consulta")).toEqual(["7"]);
});

test("C12 informa quando a data não tem consultas", async () => {
  listarConsultasDoDia.mockResolvedValue({ data: [] });
  await abrirFormulario();
  await selecionar("#data-consulta", "2026-11-23");

  expect(container.textContent).toContain("Nenhuma consulta na data selecionada.");
  expect(valoresDasOpcoes("#consulta")).toEqual([]);
});

test("C13 avisa da falha e retira as opções anteriores", async () => {
  listarConsultasDoDia.mockResolvedValueOnce({ data: [consultaNaData(2, "2026-11-24")] })
    .mockRejectedValueOnce(new Error("Sem rede"));
  await abrirFormulario();
  expect(valoresDasOpcoes("#consulta")).toEqual(["2"]);

  await selecionar("#data-consulta", "2026-11-23");

  expect(container.querySelector('[role="alert"]').textContent)
    .toContain("Não foi possível carregar as consultas.");
  expect(valoresDasOpcoes("#consulta")).toEqual([]);
  expect(container.querySelector("#consulta").disabled).toBe(true);
});

test("uma agenda anterior não é restaurada por resposta atrasada", async () => {
  const agendaAnterior = aguardarResposta();
  listarConsultasDoDia.mockImplementationOnce(() => agendaAnterior.promessa)
    .mockResolvedValueOnce({ data: [consultaNaData(7, "2026-11-23")] });
  await abrirFormulario();
  await selecionar("#data-consulta", "2026-11-23");

  await act(async () => agendaAnterior.resolver({ data: [consultaNaData(2, "2026-11-24")] }));

  expect(valoresDasOpcoes("#consulta")).toEqual(["7"]);
});

test("C16 indica carregamento e desabilita a seleção de consultas", async () => {
  const agenda = aguardarResposta();
  listarConsultasDoDia.mockImplementationOnce(() => agenda.promessa);
  await abrirFormulario();

  expect(container.querySelector('[role="status"]').textContent).toContain("Carregando consultas");
  expect(container.querySelector("#consulta").disabled).toBe(true);

  await act(async () => agenda.resolver({ data: [consultaNaData(2, "2026-11-24")] }));
  expect(container.querySelector('[role="status"]')).toBeNull();
  expect(container.querySelector("#consulta").disabled).toBe(false);
});

test("C3 mostra consultas da data do veterinário em ordem de horário", async () => {
  listarConsultasDoDia.mockImplementation(async (data) => ({
    data: data === "2026-11-23" ? [
      consultaNaData(6, data, { dataHora: data + "T08:30:00", status: "CONFIRMADA" }),
      consultaNaData(8, data, { veterinarioId: 4, petNome: "Luna" }),
      consultaNaData(7, data, { petId: 5, petNome: "Mel", dataHora: data + "T11:00:00" }),
    ] : [consultaNaData(2, data)],
  }));
  await abrirFormulario();
  await selecionar("#data-consulta", "2026-11-23");

  const opcoes = Array.from(container.querySelector("#consulta").options).slice(1);
  expect(opcoes.map((opcao) => opcao.value)).toEqual(["6", "7"]);
  expect(opcoes[0].textContent).toMatch(/08:30.*Rex.*Confirmada/);
  expect(opcoes[1].textContent).toMatch(/11:00.*Mel.*Agendada/);
  expect(container.querySelector("#pet")).toBeNull();
  expect(container.querySelector("#veterinario")).toBeNull();
});

test("C5 registra uma consulta passada com os vínculos da seleção", async () => {
  listarConsultasDoDia.mockImplementation(async (data) => ({
    data: data === "2026-11-23" ? [consultaNaData(7, data, { petId: 5 })] : [],
  }));
  registrarAtendimento.mockResolvedValue({ data: { id: 9 } });
  await abrirFormulario();
  await selecionar("#data-consulta", "2026-11-23");
  await selecionar("#consulta", "7");
  await selecionar("textarea", "Paciente tratado para otite.");

  await act(async () => Simulate.submit(container.querySelector("form")));

  expect(registrarAtendimento).toHaveBeenCalledWith({
    petId: 5, consultaId: 7, veterinarioId: 3, descricaoClinica: "Paciente tratado para otite.",
  });
  expect(container.textContent).toContain("Atendimento registrado com sucesso!");
  expect(listarConsultasDoDia).toHaveBeenLastCalledWith("2026-11-23");
});

const registroClinico = (id, consultaId, descricaoClinica) => ({
  id, consultaId, descricaoClinica,
  dataAtendimento: "2026-11-24T14:00:00",
  dataCriacao: "2026-11-24T14:00:00",
  dataAtualizacao: "2026-11-24T14:00:00",
  veterinarioNome: "Dra. Bia",
});

const registrosDaConsulta = [
  registroClinico(21, 7, "Descrição original do prontuário antigo."),
  registroClinico(22, 8, "Registro de outra consulta do mesmo pet."),
  registroClinico(23, 7, "Descrição do complemento clínico."),
];

const selecionarModoAtualizar = async () => {
  const botao = [...container.querySelectorAll("button")]
    .find((elemento) => elemento.textContent === "Atualizar Existente");
  await act(async () => Simulate.click(botao));
};

const abrirAtualizacaoDeConsultaPassada = async (registros = registrosDaConsulta) => {
  listarConsultasDoDia.mockImplementation(async (data) => ({
    data: data === "2026-11-23" ? [consultaNaData(7, data, { status: "REALIZADA" })] : [],
  }));
  listarAtendimentosPorPet.mockResolvedValue({ data: registros });
  await abrirFormulario();
  await selecionarModoAtualizar();
  await selecionar("#data-consulta", "2026-11-23");
  await selecionar("#consulta", "7");
};

test("C9 lista registros vinculados à consulta selecionada", async () => {
  await abrirAtualizacaoDeConsultaPassada();

  expect(valoresDasOpcoes("#atendimento")).toEqual(["21", "23"]);
  expect(container.querySelector('#atendimento option[value="21"]').textContent)
    .toContain("Descrição original do prontuário antigo.");
  expect(container.textContent).not.toContain("Registro de outra consulta");
});

test("C10 preenche a descrição do registro escolhido", async () => {
  await abrirAtualizacaoDeConsultaPassada();

  await selecionar("#atendimento", "21");
  expect(container.querySelector("textarea").value).toBe(registrosDaConsulta[0].descricaoClinica);
  await selecionar("#atendimento", "23");
  expect(container.querySelector("textarea").value).toBe(registrosDaConsulta[2].descricaoClinica);
});

test("C11 atualiza somente o registro selecionado", async () => {
  const descricao = "Complemento com nova orientação clínica.";
  atualizarAtendimento.mockResolvedValue({ data: registroClinico(23, 7, descricao) });
  await abrirAtualizacaoDeConsultaPassada();
  await selecionar("#atendimento", "23");
  await selecionar("textarea", descricao);

  await act(async () => Simulate.submit(container.querySelector("form")));

  expect(atualizarAtendimento).toHaveBeenCalledTimes(1);
  expect(atualizarAtendimento).toHaveBeenCalledWith({
    atendimentoId: 23, veterinarioId: 3, descricaoClinica: descricao,
  });
  expect(registrarAtendimento).not.toHaveBeenCalled();
  expect(container.textContent).toContain("Atendimento atualizado com sucesso!");
  expect(container.querySelector("#data-consulta").value).toBe("2026-11-23");
  expect(container.querySelector("#consulta").value).toBe("7");
});

test("C14 respostas antigas preservam a seleção mais recente", async () => {
  const agendaAnterior = aguardarResposta();
  const registrosAnteriores = aguardarResposta();
  listarConsultasDoDia.mockImplementationOnce(() => agendaAnterior.promessa)
    .mockResolvedValueOnce({ data: [
      consultaNaData(8, "2026-11-23", { petId: 5 }),
      consultaNaData(7, "2026-11-23"),
    ] });
  listarAtendimentosPorPet.mockImplementation((petId) => petId === 5
    ? registrosAnteriores.promessa
    : Promise.resolve({ data: registrosDaConsulta }));
  await abrirFormulario();
  await selecionarModoAtualizar();
  await selecionar("#data-consulta", "2026-11-23");
  await selecionar("#consulta", "8");
  await selecionar("#consulta", "7");
  await selecionar("#atendimento", "21");

  await act(async () => {
    agendaAnterior.resolver({ data: [consultaNaData(2, "2026-11-24")] });
    registrosAnteriores.resolver({ data: [registroClinico(22, 8, "Resposta clínica antiga.")] });
  });

  expect(valoresDasOpcoes("#consulta")).toEqual(["8", "7"]);
  expect(container.querySelector("#consulta").value).toBe("7");
  expect(valoresDasOpcoes("#atendimento")).toEqual(["21", "23"]);
  expect(container.querySelector("#atendimento").value).toBe("21");
  expect(container.querySelector("textarea").value).toBe(registrosDaConsulta[0].descricaoClinica);
});

test("C15 informa quando a consulta não tem atendimento", async () => {
  await abrirAtualizacaoDeConsultaPassada([registrosDaConsulta[1]]);

  expect(container.textContent).toContain("Esta consulta ainda não tem atendimento registrado.");
  expect(valoresDasOpcoes("#atendimento")).toEqual([]);
  expect(container.querySelector('button[type="submit"]').disabled).toBe(true);
});
