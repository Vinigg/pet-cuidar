import React, { useState } from "react";
import { obterHistoricoPet, obterResumoHistoricoPet } from "../api/api";

export default function HistoricoPet() {
  const [petId, setPetId] = useState("");
  const [resumo, setResumo] = useState(null);
  const [historico, setHistorico] = useState([]);
  const [erro, setErro] = useState(null);
  const [buscando, setBuscando] = useState(false);

  const buscar = async (e) => {
    e.preventDefault();
    setErro(null);
    setBuscando(true);
    try {
      const [resResumo, resHistorico] = await Promise.all([
        obterResumoHistoricoPet(petId),
        obterHistoricoPet(petId),
      ]);
      setResumo(resResumo.data);
      setHistorico(resHistorico.data);
    } catch {
      setErro("Pet nao encontrado ou sem historico.");
      setResumo(null);
      setHistorico([]);
    } finally {
      setBuscando(false);
    }
  };

  return (
    <div className="card">
      <h2>Historico do pet (visao do veterinario)</h2>
      <form onSubmit={buscar} className="linha-busca">
        <label>
          Id do pet
          <input required type="number" value={petId} onChange={(e) => setPetId(e.target.value)} />
        </label>
        <button type="submit" disabled={buscando}>
          {buscando ? "Buscando..." : "Buscar"}
        </button>
      </form>

      {erro && <p className="erro">{erro}</p>}

      {resumo && (
        <div className="resumo-ia">
          <h3>Resumo (gerado por IA)</h3>
          <p>{resumo.resumo}</p>
          <a href={resumo.linkHistoricoCompleto}>Ver historico completo</a>
        </div>
      )}

      {historico.length > 0 && (
        <div>
          <h3>Historico completo</h3>
          <ul className="lista-historico">
            {historico.map((h) => (
              <li key={h.id}>
                <strong>{h.data?.substring(0, 10)}</strong> — {h.descricao}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
