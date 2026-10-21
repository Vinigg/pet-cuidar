import React, { useEffect, useState } from "react";
import { listarVeterinarios, listarDisponibilidade, criarConsulta } from "../api/api";

export default function AgendamentoForm() {
  const [veterinarios, setVeterinarios] = useState([]);
  const [veterinarioId, setVeterinarioId] = useState("");
  const [data, setData] = useState("");
  const [petId, setPetId] = useState("");
  const [horarios, setHorarios] = useState([]);
  const [horarioSelecionado, setHorarioSelecionado] = useState("");
  const [mensagem, setMensagem] = useState(null);
  const [erro, setErro] = useState(null);

  useEffect(() => {
    listarVeterinarios()
      .then((res) => setVeterinarios(res.data))
      .catch(() => setErro("Nao foi possivel carregar os veterinarios."));
  }, []);

  const buscarDisponibilidade = async () => {
    if (!veterinarioId || !data) return;
    setErro(null);
    try {
      const res = await listarDisponibilidade(veterinarioId, data);
      setHorarios(res.data);
    } catch {
      setErro("Nao foi possivel carregar a disponibilidade.");
    }
  };

  const agendar = async (e) => {
    e.preventDefault();
    setMensagem(null);
    setErro(null);
    try {
      await criarConsulta({
        petId: Number(petId),
        veterinarioId: Number(veterinarioId),
        dataHora: horarioSelecionado,
      });
      setMensagem("Consulta agendada com sucesso!");
      setHorarioSelecionado("");
      buscarDisponibilidade();
    } catch (err) {
      setErro(err.response?.data?.mensagem || "Nao foi possivel agendar. O horario pode ja estar ocupado.");
    }
  };

  return (
    <div className="card">
      <h2>Agendar consulta</h2>
      <form onSubmit={agendar}>
        <label>
          Id do pet
          <input required type="number" value={petId} onChange={(e) => setPetId(e.target.value)} />
        </label>

        <label>
          Veterinario
          <select required value={veterinarioId} onChange={(e) => setVeterinarioId(e.target.value)}>
            <option value="">Selecione...</option>
            {veterinarios.map((v) => (
              <option key={v.id} value={v.id}>
                {v.nome} {v.especialidade ? `— ${v.especialidade}` : ""}
              </option>
            ))}
          </select>
        </label>

        <label>
          Data
          <input required type="date" value={data} onChange={(e) => setData(e.target.value)} />
        </label>

        <button type="button" className="btn-secundario" onClick={buscarDisponibilidade}>
          Ver horarios disponiveis
        </button>

        {horarios.length > 0 && (
          <div className="grade-horarios">
            {horarios.map((h) => (
              <button
                type="button"
                key={h.horario}
                disabled={!h.disponivel}
                className={`horario ${horarioSelecionado === h.horario ? "selecionado" : ""}`}
                onClick={() => setHorarioSelecionado(h.horario)}
              >
                {h.horario.substring(11, 16)}
              </button>
            ))}
          </div>
        )}

        <div className="acoes">
          <button type="submit" disabled={!horarioSelecionado}>
            Confirmar agendamento
          </button>
        </div>

        {mensagem && <p className="sucesso">{mensagem}</p>}
        {erro && <p className="erro">{erro}</p>}
      </form>
    </div>
  );
}
