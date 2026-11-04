import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Button, Badge } from './common/UIComponents';
import { criarConsulta, listarDisponibilidade, listarPets, listarVeterinarios } from '../api/api';

export default function AgendamentoForm({ tutorId }) {
  const [opcoesPets, setOpcoesPets] = useState([]);
  const [veterinarios, setVeterinarios] = useState([]);

  const [petId, setPetId] = useState('');
  const [veterinarioId, setVeterinarioId] = useState('');
  const [dataConsulta, setDataConsulta] = useState(new Date().toISOString().split('T')[0]);
  const [horariosDisponiveis, setHorariosDisponiveis] = useState([]);
  const [horarioSelecionado, setHorarioSelecionado] = useState('');

  const [loading, setLoading] = useState(false);
  const [mensagemSucesso, setMensagemSucesso] = useState(null);
  const [erro, setErro] = useState(null);
  const [erroPets, setErroPets] = useState(null);
  const [erroDisponibilidade, setErroDisponibilidade] = useState(null);
  const [carregandoOpcoes, setCarregandoOpcoes] = useState(true);
  const requisicaoDisponibilidade = useRef(0);

  const carregarOpcoes = useCallback(async () => {
    setCarregandoOpcoes(true);
    setErroPets(null);
    try {
      const [resPets, resVets] = await Promise.all([
        listarPets(tutorId),
        listarVeterinarios()
      ]);
      setOpcoesPets(Array.isArray(resPets.data) ? resPets.data : []);
      setVeterinarios(Array.isArray(resVets.data) ? resVets.data : []);
    } catch (err) {
      setOpcoesPets([]);
      setErroPets('Nao foi possivel carregar os pets.');
    } finally {
      setCarregandoOpcoes(false);
    }
  }, [tutorId]);

  useEffect(() => {
    carregarOpcoes();
  }, [carregarOpcoes]);

  const buscarHorarios = useCallback(async () => {
    setHorarioSelecionado('');
    setHorariosDisponiveis([]);
    setErroDisponibilidade(null);
    if (!veterinarioId || !dataConsulta) return;

    const idRequisicao = ++requisicaoDisponibilidade.current;
    try {
      const response = await listarDisponibilidade(veterinarioId, dataConsulta);
      if (idRequisicao === requisicaoDisponibilidade.current) {
        setHorariosDisponiveis(Array.isArray(response.data) ? response.data : []);
      }
    } catch (err) {
      if (idRequisicao === requisicaoDisponibilidade.current) {
        setErroDisponibilidade('Nao foi possivel consultar os horarios.');
      }
    }
  }, [veterinarioId, dataConsulta]);

  useEffect(() => {
    buscarHorarios();
    return () => {
      requisicaoDisponibilidade.current += 1;
    };
  }, [buscarHorarios]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!petId || !veterinarioId || !dataConsulta || !horarioSelecionado) {
      setErro('Por favor, selecione todas as opções e o horário desejado.');
      return;
    }

    setLoading(true);
    setErro(null);
    setMensagemSucesso(null);

    const dataHoraIso = horarioSelecionado.includes('T')
      ? horarioSelecionado
      : `${dataConsulta}T${horarioSelecionado}:00`;

    try {
      const response = await criarConsulta({
        petId: parseInt(petId, 10),
        veterinarioId: parseInt(veterinarioId, 10),
        dataHora: dataHoraIso
      });

      setMensagemSucesso(`Consulta agendada com sucesso para ${response.data.petNome || 'o pet'}!`);
  await buscarHorarios();
    } catch (err) {
      setErro(err.response?.data?.mensagem || 'Erro ao agendar consulta.');
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="booking-container">
        <div className="booking-card">
          <header className="form-header">
            <Badge variant="primary">Agendamento Online</Badge>
            <h1 className="form-title" style={{ marginTop: '8px' }}>Agendar Consulta</h1>
            <p className="form-subtitle">
              Selecione o paciente, o especialista e escolha o melhor horário disponível na clínica.
            </p>
          </header>

          {mensagemSucesso && <div className="alert-box success">{mensagemSucesso}</div>}
          {erro && <div className="alert-box error">{erro}</div>}
          {erroPets && (
            <div className="alert-box error" role="alert">
              {erroPets}
              <button type="button" className="btn-secundario" onClick={carregarOpcoes}>
                Tentar novamente
              </button>
            </div>
          )}
          {!carregandoOpcoes && !erroPets && opcoesPets.length === 0 && (
            <div className="empty-state">Nenhum pet cadastrado.</div>
          )}

          <form onSubmit={handleSubmit}>
            {/* Passo 1: Paciente e Profissional */}
            <div className="booking-step">
              <h2 className="booking-step-title">
                <span className="form-step-badge">1</span>
                Paciente & Especialista
              </h2>

              <div className="form-grid-2">
                <div className="input-group">
                  <label className="input-label">SELECIONE O PET (TUTOR)</label>
                  <select
                      className="select-custom"
                      value={petId}
                      onChange={(e) => setPetId(e.target.value)}
                      disabled={carregandoOpcoes || Boolean(erroPets) || opcoesPets.length === 0}
                      required
                  >
                    <option value="">Escolha um pet...</option>
                    {opcoesPets.map(op => (
                        <option key={op.id} value={op.id}>
                          {op.nome} - {op.especie} (Tutor: {op.tutorNome})
                        </option>
                    ))}
                  </select>
                </div>

                <div className="input-group">
                  <label className="input-label">VETERINÁRIO(A)</label>
                  <select
                      className="select-custom"
                      value={veterinarioId}
                      onChange={(e) => setVeterinarioId(e.target.value)}
                      disabled={carregandoOpcoes || veterinarios.length === 0}
                      required
                  >
                    <option value="">Escolha um(a) veterinário(a)...</option>
                    {veterinarios.map(v => (
                        <option key={v.id} value={v.id}>
                          {v.nome} - {v.especialidade}
                        </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <hr className="form-divider" />

            {/* Passo 2: Data e Grade de Horários */}
            <div className="booking-step">
              <h2 className="booking-step-title">
                <span className="form-step-badge">2</span>
                Data & Horário Disponível
              </h2>

              <div className="form-grid-2" style={{ marginBottom: '14px' }}>
                <div className="input-group">
                  <label className="input-label">DATA DA CONSULTA</label>
                  <input
                      type="date"
                      className="select-custom"
                      value={dataConsulta}
                      onChange={(e) => setDataConsulta(e.target.value)}
                      required
                  />
                </div>
              </div>

              <div className="input-group">
                <label className="input-label">GRADE DE HORÁRIOS DA AGENDA</label>

                {erroDisponibilidade ? (
                    <div className="empty-state" role="alert" style={{ padding: '20px' }}>
                      {erroDisponibilidade}
                    </div>
                ) : !veterinarioId ? (
                    <div className="empty-state" style={{ padding: '20px' }}>
                      Selecione um(a) veterinário(a) para consultar os horários livres.
                    </div>
                ) : horariosDisponiveis.length === 0 ? (
                    <div className="empty-state" style={{ padding: '20px' }}>
                      Nenhum horário disponível para esta data.
                    </div>
                ) : (
                    <div className="slots-grid">
                      {horariosDisponiveis.map((slot) => {
                        const isSelected = horarioSelecionado === slot.horario;
                        const isOccupied = slot.disponivel === false || slot.ocupado === true;
                        const horarioVisivel = slot.horario.includes('T')
                          ? slot.horario.split('T')[1].slice(0, 5)
                          : slot.horario;

                        return (
                            <button
                                key={slot.horario}
                                type="button"
                                disabled={isOccupied}
                                onClick={() => setHorarioSelecionado(slot.horario)}
                                className={`slot-btn horario ${isSelected ? 'selected' : ''}`}
                            >
                              <span>{horarioVisivel}</span>
                              <span className="slot-status-text">
                          {isOccupied ? 'Ocupado' : isSelected ? 'Escolhido' : 'Livre'}
                        </span>
                            </button>
                        );
                      })}
                    </div>
                )}
              </div>
            </div>

            <div className="form-actions-footer">
              <Button variant="primary" size="lg" type="submit" disabled={loading || !horarioSelecionado}>
                {loading ? 'Confirmando...' : 'Finalizar Agendamento'}
              </Button>
            </div>
          </form>
        </div>
      </div>
  );
}