import React, { useEffect, useRef, useState } from "react";
import { obterConsulta } from "../api/api";

export const ROTULOS_STATUS = {
  AGENDADA: "Agendada",
  CONFIRMADA: "Confirmada",
  REALIZADA: "Realizada",
  CANCELADA: "Cancelada",
  NAO_COMPARECEU: "Nao compareceu",
};

// "2026-12-10T14:30:00" -> data e horario sem conversao de fuso (a API envia horario local da clinica)
function separarDataHora(dataHora) {
  if (!dataHora) return { data: null, horario: null };
  const [data, hora = ""] = dataHora.split("T");
  const [ano, mes, dia] = data.split("-");
  return { data: `${dia}/${mes}/${ano}`, horario: hora.substring(0, 5) };
}

function Campo({ rotulo, valor, destaque }) {
  const vazio = valor === null || valor === undefined || valor === "";
  return (
    <div className="detalhe-campo">
      <dt>{rotulo}</dt>
      <dd className={destaque && !vazio ? "destaque-alerta" : vazio ? "nao-informado" : undefined}>
        {vazio ? "Nao informado" : valor}
      </dd>
    </div>
  );
}

function Secao({ titulo, children }) {
  return (
    <section className="detalhe-secao">
      <h3>{titulo}</h3>
      <dl>{children}</dl>
    </section>
  );
}

/**
 * Modal somente leitura com os detalhes de uma consulta (GET /consultas/{id}).
 * Busca apenas a consulta informada; fecha pelo botao, pela tecla Esc ou
 * clicando fora do painel.
 */
export default function DetalhesConsulta({ consultaId, onFechar }) {
  const [estado, setEstado] = useState("carregando");
  const [consulta, setConsulta] = useState(null);
  const [tentativa, setTentativa] = useState(0);
  const botaoFechar = useRef(null);

  useEffect(() => {
    let ativo = true;
    setEstado("carregando");
    setConsulta(null);
    obterConsulta(consultaId)
      .then((res) => {
        if (!ativo) return;
        setConsulta(res.data);
        setEstado("ok");
      })
      .catch((err) => {
        if (!ativo) return;
        setEstado(err?.response?.status === 404 ? "nao-encontrada" : "erro");
      });
    return () => {
      ativo = false;
    };
  }, [consultaId, tentativa]);

  useEffect(() => {
    const focoAnterior = document.activeElement;
    botaoFechar.current?.focus();
    const aoTeclar = (e) => {
      if (e.key === "Escape") onFechar();
    };
    document.addEventListener("keydown", aoTeclar);
    return () => {
      document.removeEventListener("keydown", aoTeclar);
      if (focoAnterior && typeof focoAnterior.focus === "function") focoAnterior.focus();
    };
  }, [onFechar]);

  const { data, horario } = separarDataHora(consulta?.dataHora);

  return (
    <div className="modal-fundo" onClick={(e) => e.target === e.currentTarget && onFechar()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="titulo-detalhes-consulta">
        <header className="modal-cabecalho">
          <h2 id="titulo-detalhes-consulta">Detalhes da consulta</h2>
          <button type="button" className="btn-secundario btn-fechar" ref={botaoFechar} onClick={onFechar}>
            Fechar
          </button>
        </header>

        {estado === "carregando" && (
          <p role="status" className="detalhe-carregando">
            Carregando detalhes...
          </p>
        )}

        {estado === "nao-encontrada" && (
          <p className="erro" role="alert">
            Consulta nao encontrada. Ela pode ter sido removida; atualize o painel.
          </p>
        )}

        {estado === "erro" && (
          <div role="alert">
            <p className="erro">Nao foi possivel carregar os detalhes da consulta.</p>
            <button type="button" onClick={() => setTentativa((t) => t + 1)}>
              Tentar novamente
            </button>
          </div>
        )}

        {estado === "ok" && consulta && (
          <div className="detalhe-grade">
            <Secao titulo="Consulta">
              <Campo rotulo="Codigo" valor={`#${consulta.id}`} />
              <Campo rotulo="Data" valor={data} />
              <Campo rotulo="Horario" valor={horario} />
              <div className="detalhe-campo">
                <dt>Status</dt>
                <dd>
                  <span className={`badge status-${String(consulta.status).toLowerCase()}`}>
                    {ROTULOS_STATUS[consulta.status] || consulta.status}
                  </span>
                </dd>
              </div>
            </Secao>

            <Secao titulo="Pet">
              <Campo rotulo="Nome" valor={consulta.pet?.nome} />
              <Campo rotulo="Especie" valor={consulta.pet?.especie} />
              <Campo
                rotulo="Idade"
                valor={
                  consulta.pet?.idade == null ? null : `${consulta.pet.idade} ${consulta.pet.idade === 1 ? "ano" : "anos"}`
                }
              />
              <Campo rotulo="Alergias criticas" valor={consulta.pet?.alergiasCriticas} destaque />
            </Secao>

            <Secao titulo="Tutor">
              <Campo rotulo="Nome" valor={consulta.tutor?.nome} />
              <Campo rotulo="E-mail" valor={consulta.tutor?.email} />
              <Campo rotulo="Telefone" valor={consulta.tutor?.telefone} />
            </Secao>

            <Secao titulo="Veterinario">
              <Campo rotulo="Nome" valor={consulta.veterinario?.nome} />
              <Campo rotulo="Especialidade" valor={consulta.veterinario?.especialidade} />
            </Secao>
          </div>
        )}
      </div>
    </div>
  );
}
