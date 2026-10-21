import React, { useEffect, useState, useCallback } from "react";
import { listarConsultasDoDia, obterPredicaoNoShow, dispararLembrete } from "../api/api";

function hoje() {
  return new Date().toISOString().slice(0, 10);
}

const CORES_RISCO = { BAIXO: "risco-baixo", MEDIO: "risco-medio", ALTO: "risco-alto" };

export default function PainelRecepcionista() {
  const [data, setData] = useState(hoje());
  const [consultas, setConsultas] = useState([]);
  const [riscos, setRiscos] = useState({});
  const [statusLembrete, setStatusLembrete] = useState({});
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const res = await listarConsultasDoDia(data);
      setConsultas(res.data);
    } catch {
      setErro("Nao foi possivel carregar a agenda do dia.");
    } finally {
      setCarregando(false);
    }
  }, [data]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const verRisco = async (consultaId) => {
    try {
      const res = await obterPredicaoNoShow(consultaId);
      setRiscos((prev) => ({ ...prev, [consultaId]: res.data }));
    } catch {
      setRiscos((prev) => ({ ...prev, [consultaId]: { categoria: "INDISPONIVEL", motivo: "Falha ao calcular." } }));
    }
  };

  const enviarLembrete = async (consultaId) => {
    setStatusLembrete((prev) => ({ ...prev, [consultaId]: "enviando" }));
    try {
      const res = await dispararLembrete(consultaId);
      setStatusLembrete((prev) => ({
        ...prev,
        [consultaId]: res.data.usouFallback ? "enviado-fallback" : "enviado",
      }));
    } catch {
      setStatusLembrete((prev) => ({ ...prev, [consultaId]: "falhou" }));
    }
  };

  return (
    <div className="card">
      <h2>Painel do dia — recepcionista</h2>
      <label>
        Data
        <input type="date" value={data} onChange={(e) => setData(e.target.value)} />
      </label>

      {carregando && <p>Carregando...</p>}
      {erro && <p className="erro">{erro}</p>}

      <table className="tabela-consultas">
        <thead>
          <tr>
            <th>Horario</th>
            <th>Pet</th>
            <th>Tutor</th>
            <th>Veterinario</th>
            <th>Status</th>
            <th>Risco de falta</th>
            <th>Lembrete</th>
          </tr>
        </thead>
        <tbody>
          {consultas.map((c) => {
            const risco = riscos[c.id];
            const lembreteStatus = statusLembrete[c.id];
            return (
              <tr key={c.id}>
                <td>{c.dataHora.substring(11, 16)}</td>
                <td>{c.petNome}</td>
                <td>{c.tutorNome}</td>
                <td>{c.veterinarioNome}</td>
                <td>{c.status}</td>
                <td>
                  {risco ? (
                    <span className={`badge ${CORES_RISCO[risco.categoria] || ""}`} title={risco.motivo}>
                      {risco.categoria}
                    </span>
                  ) : (
                    <button type="button" className="btn-link" onClick={() => verRisco(c.id)}>
                      Calcular
                    </button>
                  )}
                </td>
                <td>
                  <button type="button" className="btn-link" onClick={() => enviarLembrete(c.id)}>
                    {lembreteStatus === "enviando" ? "Enviando..." : "Enviar lembrete"}
                  </button>
                  {lembreteStatus === "enviado" && <span className="sucesso"> ✓ enviado (IA)</span>}
                  {lembreteStatus === "enviado-fallback" && <span className="aviso"> ✓ enviado (template)</span>}
                  {lembreteStatus === "falhou" && <span className="erro"> falhou</span>}
                </td>
              </tr>
            );
          })}
          {consultas.length === 0 && !carregando && (
            <tr>
              <td colSpan="7">Nenhuma consulta agendada para esta data.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
