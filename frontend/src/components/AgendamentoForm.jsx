import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Button, Badge } from './common/UIComponents';
import { criarConsulta, listarDisponibilidade, listarPets, listarVeterinarios, reagendarConsulta } from '../api/api';
import CalendarioConsulta from './CalendarioConsulta';
import SelecaoTutorPet from './SelecaoTutorPet';

export default function AgendamentoForm({ tutorId, consulta, onSucesso, onCancelar, visualTutor = false }) {
  const [opcoesPets, setOpcoesPets] = useState([]);
  const [veterinarios, setVeterinarios] = useState([]);
  const [tutorSelecionado, setTutorSelecionado] = useState(tutorId ? String(tutorId) : '');

  const [petId, setPetId] = useState(consulta ? String(consulta.petId) : '');
  const [veterinarioId, setVeterinarioId] = useState(consulta ? String(consulta.veterinarioId) : '');
  const [dataConsulta, setDataConsulta] = useState(consulta?.dataHora.split('T')[0] || new Date().toISOString().split('T')[0]);
  const [horariosDisponiveis, setHorariosDisponiveis] = useState([]);
  const [horarioSelecionado, setHorarioSelecionado] = useState('');

  const [loading, setLoading] = useState(false);
  const [mensagemSucesso, setMensagemSucesso] = useState(null);
  const [erro, setErro] = useState(null);
  const [erroPets, setErroPets] = useState(null);
  const [erroDisponibilidade, setErroDisponibilidade] = useState(null);
  const [carregandoOpcoes, setCarregandoOpcoes] = useState(true);
  const requisicaoDisponibilidade = useRef(0);
  const enviando = useRef(false);

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
      if (consulta) {
        const paciente = resPets.data.find(p => String(p.id) === String(consulta.petId));
        setTutorSelecionado(paciente?.tutorId != null ? String(paciente.tutorId) : '');
      }
    } catch (err) {
      setOpcoesPets([]);
      setErroPets('Nao foi possivel carregar os pets.');
    } finally {
      setCarregandoOpcoes(false);
    }
  }, [tutorId, consulta]);

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
    if (enviando.current) return;
    if (!visualTutor && (!tutorSelecionado || !opcoesPets.some(p =>
        String(p.id) === petId && String(p.tutorId) === tutorSelecionado))) {
      setErro('Selecione o tutor e um pet vinculado a ele.');
      return;
    }
    if (!petId || !veterinarioId || !dataConsulta || !horarioSelecionado) {
      setErro('Por favor, selecione todas as opções e o horário desejado.');
      return;
    }

    enviando.current = true;
    setLoading(true);
    setErro(null);
    setMensagemSucesso(null);

    const dataHoraIso = horarioSelecionado.includes('T')
      ? horarioSelecionado
      : `${dataConsulta}T${horarioSelecionado}:00`;

    try {
      const response = consulta ? await reagendarConsulta(consulta.id, dataHoraIso) : await criarConsulta({
        petId: parseInt(petId, 10),
        veterinarioId: parseInt(veterinarioId, 10),
        dataHora: dataHoraIso
      });
      if (onSucesso) {
        onSucesso(response.data);
      } else {
        setMensagemSucesso(`Consulta agendada com sucesso para ${response.data.petNome || 'o pet'}!`);
        await buscarHorarios();
      }
    } catch (err) {
      setErro(err.response?.data?.mensagem || 'Não foi possível salvar o agendamento.');
      if (err.response?.status === 409) await buscarHorarios();
    } finally {
      enviando.current = false;
      setLoading(false);
    }
  };

  return (
      <div className={visualTutor ? 'tutor-page-wrapper booking-two-columns-layout' : 'booking-container'}>
        <div className="booking-card">
          <header className="form-header">
            <Badge variant="primary">Agendamento Online</Badge>
            <h1 className="form-title" style={{ marginTop: '8px' }}>{consulta ? 'Remarcar consulta' : 'Agendar Consulta'}</h1>
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

              {visualTutor ? (
                <div>
                  <h3 className="section-head-title">🐾 Qual pet vai à consulta?</h3>
                  <div className="pet-pick-cards-grid">
                    {opcoesPets.map(p => <button key={p.id} type="button"
                      disabled={Boolean(consulta) || loading}
                      className={`pet-pick-card ${petId === String(p.id) ? 'selected' : ''}`}
                      aria-pressed={petId === String(p.id)} onClick={() => setPetId(String(p.id))}>
                      <span className="pet-pick-avatar" aria-hidden="true">{p.especie?.toLowerCase() === 'gato' ? '🐈' : '🐕'}</span>
                      <strong>{p.nome}</strong><span>{p.especie}</span>
                    </button>)}
                  </div>
                  <h3 className="section-head-title">🩺 Escolha o veterinário</h3>
                  <div className="vet-selection-list">
                    {veterinarios.map(v => <button key={v.id} type="button"
                      disabled={Boolean(consulta) || loading}
                      className={`vet-card-option ${veterinarioId === String(v.id) ? 'selected' : ''}`}
                      aria-pressed={veterinarioId === String(v.id)} onClick={() => setVeterinarioId(String(v.id))}>
                      <span className="vet-card-left"><span className="vet-photo-circle" aria-hidden="true">🩺</span>
                      <span><strong>{v.nome}</strong><span className="restored-block">{v.especialidade}</span></span></span>
                    </button>)}
                  </div>
                </div>
              ) : <>
                <SelecaoTutorPet pets={opcoesPets} tutorId={tutorSelecionado} petId={petId}
                  prefixo="agendamento" carregando={carregandoOpcoes} disabled={Boolean(consulta) || loading}
                  onTutorChange={id => {
                    setTutorSelecionado(id); setPetId(''); setHorarioSelecionado('');
                    setMensagemSucesso(null); setErro(null);
                  }}
                  onPetChange={setPetId} />
                <div className="form-grid-2">
                <div className="input-group">
                  <label className="input-label" htmlFor="agendamento-veterinario">VETERINÁRIO(A)</label>
                  <select
                      id="agendamento-veterinario"
                      className="select-custom"
                      value={veterinarioId}
                      onChange={(e) => setVeterinarioId(e.target.value)}
                      disabled={Boolean(consulta) || carregandoOpcoes || veterinarios.length === 0}
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
              </>}
            </div>

            <hr className="form-divider" />

            {/* Passo 2: Data e Grade de Horários */}
            <div className="booking-step">
              <h2 className="booking-step-title">
                <span className="form-step-badge">2</span>
                Data & Horário Disponível
              </h2>

              {visualTutor && <CalendarioConsulta data={dataConsulta} onSelecionar={setDataConsulta} disabled={loading} />}
              <div className="form-grid-2" style={{ marginBottom: '14px' }}>
                <div className="input-group">
                  <label className="input-label">DATA DA CONSULTA</label>
                  <input
                      type="date"
                      className="select-custom"
                      value={dataConsulta}
                      disabled={loading}
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
                                disabled={isOccupied || loading}
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
              {onCancelar && <Button type="button" variant="outlined" onClick={onCancelar}>Voltar</Button>}
              <Button variant="primary" size="lg" type="submit" disabled={loading || !horarioSelecionado}>
                {loading ? 'Confirmando...' : consulta ? 'Confirmar remarcação' : 'Finalizar Agendamento'}
              </Button>
            </div>
          </form>
        </div>
        {visualTutor && <aside className="booking-summary-side-card">
          <span className="tutor-pill-tag">RESUMO DO AGENDAMENTO</span>
          <h2 className="section-head-title">Confira sua escolha</h2>
          <dl className="restored-detail-grid">
            <div><dt>PACIENTE</dt><dd>{opcoesPets.find(p => String(p.id) === petId)?.nome || 'Selecione um pet'}</dd></div>
            <div><dt>PROFISSIONAL</dt><dd>{veterinarios.find(v => String(v.id) === veterinarioId)?.nome || 'Selecione um profissional'}</dd></div>
            <div><dt>DATA</dt><dd>{dataConsulta ? dataConsulta.split('-').reverse().join('/') : 'Selecione uma data'}</dd></div>
            <div><dt>HORÁRIO</dt><dd>{horarioSelecionado ? horarioSelecionado.includes('T') ? horarioSelecionado.split('T')[1].slice(0, 5) : horarioSelecionado : 'Selecione um horário'}</dd></div>
          </dl>
          <p>A reserva será confirmada após a validação de disponibilidade pela clínica.</p>
        </aside>}
      </div>
  );
}
