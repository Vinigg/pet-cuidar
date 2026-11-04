import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { Button, Input, Badge } from './common/UIComponents';
import ModalEnviarLembrete from './ModalEnviarLembrete';

export default function PainelRecepcionista() {
  const [dataSelecionada, setDataSelecionada] = useState(new Date().toISOString().split('T')[0]);
  const [consultas, setConsultas] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filtroTexto, setFiltroTexto] = useState('');

  const [modalLembreteAberto, setModalLembreteAberto] = useState(false);
  const [consultaSelecionada, setConsultaSelecionada] = useState(null);

  const carregarConsultas = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get(`http://localhost:8080/consultas?data=${dataSelecionada}`);
      setConsultas(response.data || []);
    } catch (err) {
      console.error('Erro ao buscar consultas:', err);
    } finally {
      setLoading(false);
    }
  }, [dataSelecionada]);

  useEffect(() => {
    carregarConsultas();
  }, [carregarConsultas]);

  const handleAtualizarStatus = async (consultaId, novoStatus) => {
    try {
      await axios.patch(`http://localhost:8080/consultas/${consultaId}/status`, {
        status: novoStatus
      });
      carregarConsultas();
    } catch (err) {
      alert(err.response?.data?.mensagem || 'Não foi possível atualizar o status.');
    }
  };

  const abrirModalLembrete = (consulta) => {
    setConsultaSelecionada(consulta);
    setModalLembreteAberto(true);
  };

  const totalConsultas = consultas.length;
  const confirmadas = consultas.filter(c => c.status === 'CONFIRMADA').length;
  const pendentes = consultas.filter(c => c.status === 'AGENDADA').length;
  const faltas = consultas.filter(c => c.status === 'NAO_COMPARECEU').length;

  const consultasFiltradas = consultas.filter(c =>
      c.petNome?.toLowerCase().includes(filtroTexto.toLowerCase()) ||
      c.tutorNome?.toLowerCase().includes(filtroTexto.toLowerCase())
  );

  const renderRiscoBadge = (predicao) => {
    if (!predicao) return <Badge variant="neutral">Sem Score</Badge>;
    const score = predicao.score ?? 0;

    if (score >= 0.7) {
      return (
          <Badge variant="danger" title={predicao.motivo || 'Alto risco de no-show'}>
            ● Alto ({Math.round(score * 100)}%)
          </Badge>
      );
    } else if (score >= 0.4) {
      return (
          <Badge variant="warning" title={predicao.motivo || 'Médio risco de no-show'}>
            ● Médio ({Math.round(score * 100)}%)
          </Badge>
      );
    }
    return (
        <Badge variant="success" title={predicao.motivo || 'Baixo risco de no-show'}>
          ● Baixo ({Math.round(score * 100)}%)
        </Badge>
    );
  };

  const renderStatusBadge = (status) => {
    switch (status) {
      case 'CONFIRMADA':
        return <Badge variant="success">Confirmada</Badge>;
      case 'REALIZADA':
        return <Badge variant="primary">Realizada</Badge>;
      case 'CANCELADA':
      case 'NAO_COMPARECEU':
        return <Badge variant="danger">{status === 'CANCELADA' ? 'Cancelada' : 'Não compareceu'}</Badge>;
      case 'AGENDADA':
      default:
        return <Badge variant="warning">Agendada</Badge>;
    }
  };

  return (
      <div className="panel-container">
        <header className="panel-header">
          <div>
            <h1 className="panel-title">Painel Diário da Recepção</h1>
            <p className="panel-subtitle">Controle a agenda diária e priorize confirmações de presença com apoio de IA.</p>
          </div>
          <div className="date-selector-wrapper">
            <input
                type="date"
                value={dataSelecionada}
                onChange={(e) => setDataSelecionada(e.target.value)}
                className="date-input-field"
            />
          </div>
        </header>

        <section className="metrics-grid">
          <div className="metric-card">
            <span className="metric-label">TOTAL DO DIA</span>
            <span className="metric-value">{totalConsultas}</span>
          </div>
          <div className="metric-card">
            <span className="metric-label metric-confirmed">CONFIRMADAS</span>
            <span className="metric-value metric-confirmed">{confirmadas}</span>
          </div>
          <div className="metric-card">
            <span className="metric-label metric-pending">PENDENTES</span>
            <span className="metric-value metric-pending">{pendentes}</span>
          </div>
          <div className="metric-card">
            <span className="metric-label metric-noshow">FALTAS (NO-SHOW)</span>
            <span className="metric-value metric-noshow">{faltas}</span>
          </div>
        </section>

        <section className="table-card">
          <div className="table-toolbar">
            <div className="search-box-wrapper">
              <Input
                  placeholder="Buscar por pet ou tutor..."
                  value={filtroTexto}
                  onChange={(e) => setFiltroTexto(e.target.value)}
              />
            </div>
            <Button variant="outlined" size="sm" onClick={carregarConsultas}>
              Atualizar Agenda
            </Button>
          </div>

          {loading ? (
              <div className="empty-state">Carregando agenda do dia...</div>
          ) : consultasFiltradas.length === 0 ? (
              <div className="empty-state">Nenhuma consulta encontrada para a data selecionada.</div>
          ) : (
              <table className="panel-table">
                <thead>
                <tr>
                  <th>HORÁRIO</th>
                  <th>PACIENTE & TUTOR</th>
                  <th>VETERINÁRIO(A)</th>
                  <th>STATUS</th>
                  <th>RISCO FALTA (IA)</th>
                  <th style={{ textAlign: 'right' }}>AÇÕES</th>
                </tr>
                </thead>
                <tbody>
                {consultasFiltradas.map((c) => (
                    <tr key={c.id}>
                      <td style={{ fontWeight: '700' }}>
                        {new Date(c.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                      </td>
                      <td>
                        <div className="pet-name-cell">{c.petNome}</div>
                        <div className="tutor-name-cell">{c.tutorNome}</div>
                      </td>
                      <td>{c.veterinarioNome || 'Dr(a). Veterinário(a)'}</td>
                      <td>{renderStatusBadge(c.status)}</td>
                      <td>{renderRiscoBadge(c.predicaoNoShow)}</td>
                      <td>
                        <div className="actions-cell">
                          <Button
                              variant="outlined"
                              size="sm"
                              onClick={() => abrirModalLembrete(c)}
                          >
                            Lembrete
                          </Button>
                          {c.status === 'AGENDADA' && (
                              <Button
                                  variant="primary"
                                  size="sm"
                                  onClick={() => handleAtualizarStatus(c.id, 'CONFIRMADA')}
                              >
                                Confirmar
                              </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                ))}
                </tbody>
              </table>
          )}
        </section>

        <ModalEnviarLembrete
            isOpen={modalLembreteAberto}
            onClose={() => setModalLembreteAberto(false)}
            consulta={consultaSelecionada}
            onLembreteEnviado={() => carregarConsultas()}
        />
      </div>
  );
}