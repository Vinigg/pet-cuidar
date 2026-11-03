import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { Button, Badge } from './common/UIComponents';

export default function AgendamentoForm() {
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

  // 1. Carrega pets e veterinários
  useEffect(() => {
    async function carregarDadosIniciais() {
      try {
        const [resPets, resVets] = await Promise.all([
          axios.get('http://localhost:8080/pets/opcoes'),
          axios.get('http://localhost:8080/veterinarios')
        ]);
        setOpcoesPets(resPets.data || []);
        setVeterinarios(resVets.data || []);
      } catch (err) {
        console.error('Erro ao carregar opções iniciais:', err);
      }
    }
    carregarDadosIniciais();
  }, []);

  // 2. Consulta disponibilidade da agenda
  const buscarHorarios = useCallback(async () => {
    if (!veterinarioId || !dataConsulta) return;
    try {
      const response = await axios.get(
          `http://localhost:8080/consultas/disponibilidade?veterinarioId=${veterinarioId}&data=${dataConsulta}`
      );
      setHorariosDisponiveis(response.data?.horarios || []);
      setHorarioSelecionado('');
    } catch (err) {
      console.error('Erro ao consultar horários:', err);
      setHorariosDisponiveis([]);
    }
  }, [veterinarioId, dataConsulta]);

  useEffect(() => {
    buscarHorarios();
  }, [buscarHorarios]);

  // 3. Salva a consulta
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!petId || !veterinarioId || !dataConsulta || !horarioSelecionado) {
      setErro('Por favor, selecione todas as opções e o horário desejado.');
      return;
    }

    setLoading(true);
    setErro(null);
    setMensagemSucesso(null);

    const dataHoraIso = `${dataConsulta}T${horarioSelecionado}:00`;

    try {
      const response = await axios.post('http://localhost:8080/consultas', {
        petId: parseInt(petId, 10),
        veterinarioId: parseInt(veterinarioId, 10),
        dataHora: dataHoraIso
      });

      setMensagemSucesso(`Consulta agendada com sucesso para ${response.data.petNome || 'o pet'}!`);
      buscarHorarios();
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
                      required
                  >
                    <option value="">Escolha um pet...</option>
                    {opcoesPets.map(op => (
                        <option key={op.petId} value={op.petId}>
                          {op.petNome} (Tutor: {op.tutorNome})
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

                {!veterinarioId ? (
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
                        const isOccupied = slot.ocupado;

                        return (
                            <button
                                key={slot.horario}
                                type="button"
                                disabled={isOccupied}
                                onClick={() => setHorarioSelecionado(slot.horario)}
                                className={`slot-btn ${isSelected ? 'selected' : ''}`}
                            >
                              <span>{slot.horario}</span>
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