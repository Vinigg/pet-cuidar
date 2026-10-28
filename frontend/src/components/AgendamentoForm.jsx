import React, { useEffect, useRef, useState } from "react";
import { listarPets, listarVeterinarios, listarDisponibilidade, criarConsulta } from "../api/api";

export default function AgendamentoForm({ tutorId }) {
  const [pets, setPets] = useState([]);
  const [carregandoPets, setCarregandoPets] = useState(true);
  const [erroPets, setErroPets] = useState(null);
  const [tentativaPets, setTentativaPets] = useState(0);
  const [veterinarios, setVeterinarios] = useState([]);
  const [veterinarioId, setVeterinarioId] = useState("");
  const [data, setData] = useState("");
  const [petId, setPetId] = useState("");
  const [horarios, setHorarios] = useState([]);
  const [horarioSelecionado, setHorarioSelecionado] = useState("");
  const [mensagem, setMensagem] = useState(null);
  const [erro, setErro] = useState(null);
  const [carregandoHorarios, setCarregandoHorarios] = useState(false);
  const [consultouHorarios, setConsultouHorarios] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const buscaAtual = useRef(0);
  const hoje = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);

  useEffect(() => {
    let ativo = true;
    setCarregandoPets(true);
    setErroPets(null);
    setPets([]);
    setPetId("");
    listarPets(tutorId)
      .then((res) => { if (ativo) setPets(res.data); })
      .catch(() => { if (ativo) setErroPets("Nao foi possivel carregar os pets."); })
      .finally(() => { if (ativo) setCarregandoPets(false); });
    return () => { ativo = false; };
  }, [tutorId, tentativaPets]);

  useEffect(() => {
    let ativo = true;
    listarVeterinarios()
      .then((res) => { if (ativo) setVeterinarios(res.data); })
      .catch(() => { if (ativo) setErro("Nao foi possivel carregar os veterinarios."); });
    return () => { ativo = false; buscaAtual.current += 1; };
  }, []);

  const limparHorarios = () => {
    buscaAtual.current += 1;
    setHorarios([]);
    setHorarioSelecionado("");
    setConsultouHorarios(false);
    setCarregandoHorarios(false);
    setMensagem(null);
    setErro(null);
  };

  const buscarDisponibilidade = async () => {
    if (!veterinarioId || !data || data < hoje) return;
    const busca = ++buscaAtual.current;
    setErro(null);
    setHorarios([]);
    setHorarioSelecionado("");
    setConsultouHorarios(false);
    setCarregandoHorarios(true);
    try {
      const res = await listarDisponibilidade(veterinarioId, data);
      if (busca !== buscaAtual.current) return;
      setHorarios(res.data);
      setConsultouHorarios(true);
    } catch {
      if (busca === buscaAtual.current) setErro("Nao foi possivel carregar a disponibilidade.");
    } finally {
      if (busca === buscaAtual.current) setCarregandoHorarios(false);
    }
  };

  const agendar = async (e) => {
    e.preventDefault();
    if (enviando) return;
    setMensagem(null);
    setErro(null);
    if (!pets.some((pet) => String(pet.id) === petId) || !veterinarioId || !data || data < hoje ||
      !horarios.some((horario) => horario.horario === horarioSelecionado && horario.disponivel)) {
      setErro("Selecione um pet, um veterinario, uma data valida e um horario disponivel.");
      return;
    }
    setEnviando(true);
    try {
      await criarConsulta({
        petId: Number(petId),
        veterinarioId: Number(veterinarioId),
        dataHora: horarioSelecionado,
      });
      setMensagem("Consulta agendada com sucesso!");
      setHorarioSelecionado("");
      await buscarDisponibilidade();
    } catch (err) {
      setErro(err.response?.data?.mensagem || "Nao foi possivel agendar. O horario pode ja estar ocupado.");
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="card">
      <h2>Agendar consulta</h2>
      <form onSubmit={agendar}>
        <label>
          Pet
          <select required value={petId} disabled={carregandoPets || Boolean(erroPets) || pets.length === 0 || enviando} onChange={(e) => { setPetId(e.target.value); setMensagem(null); }}>
            <option value="">{carregandoPets ? "Carregando pets..." : "Selecione um pet..."}</option>
            {pets.map((pet) => (
              <option key={pet.id} value={pet.id}>
                {pet.nome}{pet.especie ? ` - ${pet.especie}` : ""}{pet.tutorNome ? ` (Tutor: ${pet.tutorNome})` : ""}
              </option>
            ))}
          </select>
        </label>
        {erroPets && <div role="alert"><p className="erro">{erroPets}</p><button type="button" className="btn-secundario" onClick={() => setTentativaPets((atual) => atual + 1)}>Tentar novamente</button></div>}
        {!carregandoPets && !erroPets && pets.length === 0 && <p role="status">Nenhum pet cadastrado.</p>}

        <label>
          Veterinario
          <select required value={veterinarioId} disabled={enviando} onChange={(e) => { setVeterinarioId(e.target.value); limparHorarios(); }}>
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
          <input required type="date" min={hoje} value={data} disabled={enviando} onChange={(e) => { setData(e.target.value); limparHorarios(); }} />
        </label>

        <button type="button" className="btn-secundario" onClick={buscarDisponibilidade} disabled={!veterinarioId || !data || data < hoje || carregandoHorarios || enviando}>
          {carregandoHorarios ? "Buscando horarios..." : "Ver horarios disponiveis"}
        </button>

        {consultouHorarios && !horarios.some((horario) => horario.disponivel) && <p role="status">Nenhum horario disponivel nesta data.</p>}

        {horarios.length > 0 && (
          <div className="grade-horarios">
            {horarios.map((h) => (
              <button
                type="button"
                key={h.horario}
                disabled={!h.disponivel || enviando}
                aria-pressed={horarioSelecionado === h.horario}
                className={`horario ${horarioSelecionado === h.horario ? "selecionado" : ""}`}
                onClick={() => setHorarioSelecionado(h.horario)}
              >
                {h.horario.substring(11, 16)}
              </button>
            ))}
          </div>
        )}

        <div className="acoes">
          <button type="submit" disabled={!petId || !horarioSelecionado || carregandoHorarios || enviando}>
            {enviando ? "Agendando..." : "Confirmar agendamento"}
          </button>
        </div>

        {mensagem && <p className="sucesso" role="status">{mensagem}</p>}
        {erro && <p className="erro" role="alert">{erro}</p>}
      </form>
    </div>
  );
}
