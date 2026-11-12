import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { Button, Input, Badge } from './common/UIComponents';
import ModalEnviarLembrete from './ModalEnviarLembrete';

const CONSULTAS_FIGMA_MOCK = [
  {
    id: 1,
    dataHora: '2026-10-22T08:30:00',
    petNome: 'Thor',
    raca: 'Golden Retriever',
    tutorNome: 'Mariana Silva',
    veterinarioNome: 'Dra. Ana Souza',
    sala: 'Sala 03 • Retorno Clínico',
    status: 'EM_ATENDIMENTO',
    riscoNivel: 'Baixo',
    predicaoNoShow: { score: 0.12, motivo: 'Presença confirmada' },
    detalheChegada: 'Chegou às 08:24'
  },
  {
    id: 2,
    dataHora: '2026-10-22T09:15:00',
    petNome: 'Pipoca',
    raca: 'Poodle',
    tutorNome: 'Carlos Prado',
    veterinarioNome: 'Dr. Carlos Mendes',
    sala: 'Sala 02 • Vacinação V10',
    status: 'AGUARDANDO_RECEPCAO',
    riscoNivel: 'Baixo',
    predicaoNoShow: { score: 0.18, motivo: 'Baixo histórico de ausência' },
    detalheChegada: 'Aguardando há 10m'
  },
  {
    id: 3,
    dataHora: '2026-10-22T10:00:00',
    petNome: 'Luna',
    raca: 'Siamês',
    tutorNome: 'Roberto Mendes',
    veterinarioNome: 'Dr. Vinícius Lima',
    sala: 'Sala 07 • Avaliação Oftalmo',
    status: 'CONFIRMADA',
    riscoNivel: 'Baixo',
    predicaoNoShow: { score: 0.15, motivo: 'Confirmado por WhatsApp' },
    detalheChegada: 'Em 25 minutos'
  },
  {
    id: 4,
    dataHora: '2026-10-22T10:45:00',
    petNome: 'Simba',
    raca: 'Gato Persa',
    tutorNome: 'Juliana Faria',
    tutorTelefone: '(11) 98765-4321',
    veterinarioNome: 'Dra. Camila Duarte',
    sala: 'Sala 01 • Ultrassom Abdominal',
    status: 'AGENDADA',
    riscoNivel: 'Alto',
    predicaoNoShow: { score: 0.88, motivo: '2 faltas consecutivas sem aviso prévio' },
    detalheChegada: 'Sem confirmação'
  },
  {
    id: 5,
    dataHora: '2026-10-22T11:30:00',
    petNome: 'Mel',
    raca: 'SRD',
    tutorNome: 'Fernando Soares',
    veterinarioNome: 'Dra. Ana Souza',
    sala: 'Sala 03 • Dermatologia',
    status: 'AGENDADA',
    riscoNivel: 'Médio',
    predicaoNoShow: { score: 0.45, motivo: 'Lembrete entregue, pendente resposta' },
    detalheChegada: 'Lembrete entregue'
  },
  {
    id: 6,
    dataHora: '2026-10-22T14:00:00',
    petNome: 'Nina',
    raca: 'Yorkshire',
    tutorNome: 'Beatriz Nogueira',
    veterinarioNome: 'Dr. Carlos Mendes',
    sala: 'Sala 02 • Check-up Geriátrico',
    status: 'CONFIRMADA',
    riscoNivel: 'Baixo',
    predicaoNoShow: { score: 0.10, motivo: 'Confirmada às 07:40' },
    detalheChegada: 'Confirmada às 07:40'
  },
  {
    id: 7,
    dataHora: '2026-10-22T14:45:00',
    petNome: 'Billy',
    raca: 'Buldogue Francês',
    tutorNome: 'Rodrigo Alencar',
    tutorTelefone: '(11) 97123-8899',
    veterinarioNome: 'Dra. Ana Souza',
    sala: 'Sala 03 • Avaliação Cardíaca',
    status: 'AGENDADA',
    riscoNivel: 'Alto',
    predicaoNoShow: { score: 0.82, motivo: 'Agendada há mais de 30 dias sem confirmação do lembrete' },
    detalheChegada: 'Marcada há 32 dias'
  },
  {
    id: 8,
    dataHora: '2026-10-22T16:00:00',
    petNome: 'Pipoca F.',
    raca: 'Poodle Toy',
    tutorNome: 'Fernanda Costa',
    tutorTelefone: '(11) 96543-1122',
    veterinarioNome: 'Dr. Vinícius Lima',
    sala: 'Sala 07 • Procedimento Odonto',
    status: 'AGENDADA',
    riscoNivel: 'Alto',
    predicaoNoShow: { score: 0.92, motivo: 'Mensagem de WhatsApp não entregue / provável novo contato' },
    detalheChegada: 'Lembrete falhou'
  }
];

export default function PainelRecepcionista({ onNovoAgendamento }) {
  const [dataSelecionada, setDataSelecionada] = useState('2026-10-22');
  const [consultas, setConsultas] = useState(CONSULTAS_FIGMA_MOCK);
  const [loading, setLoading] = useState(false);
  const [filtroTexto, setFiltroTexto] = useState('');
  const [filtroAba, setFiltroAba] = useState('todos');

  const [modalLembreteAberto, setModalLembreteAberto] = useState(false);
  const [consultaSelecionada, setConsultaSelecionada] = useState(null);
  const [modalStatusAberto, setModalStatusAberto] = useState(false);
  const [novoStatus, setNovoStatus] = useState('CONFIRMADA');

  // Busca do Backend com fallback automático
  const carregarConsultas = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get(`http://localhost:8080/consultas?data=${dataSelecionada}`);
      if (response.data && response.data.length > 0) {
        setConsultas(response.data);
      } else {
        setConsultas(CONSULTAS_FIGMA_MOCK);
      }
    } catch {
      setConsultas(CONSULTAS_FIGMA_MOCK);
    } finally {
      setLoading(false);
    }
  }, [dataSelecionada]);

  useEffect(() => {
    carregarConsultas();
  }, [carregarConsultas]);

  // CÁLCULO DINÂMICO E AUTOMÁTICO DAS MÉTRICAS
  const totalConsultas = consultas.length;
  const confirmadas = consultas.filter((c) => c.status === 'CONFIRMADA' || c.status === 'EM_ATENDIMENTO').length;
  const porcentagemConfirmadas = totalConsultas > 0 ? Math.round((confirmadas / totalConsultas) * 100) : 0;

  const altoRiscoList = consultas.filter((c) =>
      c.riscoNivel === 'Alto' || (c.predicaoNoShow && c.predicaoNoShow.score >= 0.7)
  );
  const altoRiscoCount = altoRiscoList.length;

  const lembretesPendentes = consultas.filter((c) => c.status === 'AGENDADA').length;

  const handleAtualizarStatus = async (consultaId, statusEscolhido) => {
    try {
      await axios.patch(`http://localhost:8080/consultas/${consultaId}/status`, {
        status: statusEscolhido
      });
      carregarConsultas();
    } catch {
      setConsultas((prev) =>
          prev.map((c) => (c.id === consultaId ? { ...c, status: statusEscolhido } : c))
      );
    } finally {
      setModalStatusAberto(false);
    }
  };

  const abrirModalLembrete = (consulta) => {
    setConsultaSelecionada(consulta);
    setModalLembreteAberto(true);
  };

  const abrirModalStatus = (consulta) => {
    setConsultaSelecionada(consulta);
    setNovoStatus(consulta.status || 'CONFIRMADA');
    setModalStatusAberto(true);
  };

  const consultasFiltradas = consultas.filter((c) => {
    const busca = (c.petNome + ' ' + c.tutorNome).toLowerCase().includes(filtroTexto.toLowerCase());
    if (filtroAba === 'confirmados') return busca && (c.status === 'CONFIRMADA' || c.status === 'EM_ATENDIMENTO');
    if (filtroAba === 'risco') return busca && (c.riscoNivel === 'Alto' || (c.predicaoNoShow && c.predicaoNoShow.score >= 0.7));
    return busca;
  });

  const renderStatusBadge = (status) => {
    switch (status) {
      case 'EM_ATENDIMENTO':
        return <Badge variant="primary">● Em atendimento</Badge>;
      case 'AGUARDANDO_RECEPCAO':
        return <Badge variant="warning">⏳ Aguardando recepção</Badge>;
      case 'CONFIRMADA':
        return <Badge variant="success">✓ Confirmada</Badge>;
      case 'CANCELADA':
      case 'NAO_COMPARECEU':
        return <Badge variant="danger">{status === 'CANCELADA' ? 'Cancelada' : 'Não compareceu'}</Badge>;
      case 'AGENDADA':
      default:
        return <Badge variant="neutral">● Agendada</Badge>;
    }
  };

  const renderRiscoBadge = (item) => {
    const risco = item.riscoNivel || (item.predicaoNoShow?.score >= 0.7 ? 'Alto' : item.predicaoNoShow?.score >= 0.4 ? 'Médio' : 'Baixo');
    if (risco === 'Alto') return <span style={{ color: 'var(--color-danger)', fontWeight: 800, fontSize: '0.78rem' }}>! Alto</span>;
    if (risco === 'Médio') return <span style={{ color: 'var(--color-secondary)', fontWeight: 800, fontSize: '0.78rem' }}>Médio</span>;
    return <span style={{ color: 'var(--color-tertiary)', fontWeight: 800, fontSize: '0.78rem' }}>Baixo</span>;
  };

  return (
      <div className="clinica-container">
        <header className="panel-header" style={{ marginBottom: '20px' }}>
          <div>
          <span style={{ fontSize: '0.72rem', fontWeight: 800, color: 'var(--color-primary)' }}>
            ● VISÃO OPERACIONAL EM TEMPO REAL
          </span>
            <h1 className="panel-title" style={{ marginTop: '4px' }}>Painel do Dia</h1>
            <p className="panel-subtitle">Gerenciamento dinâmico de triagem, fluxo de chegada e mitigação ativa de absenteísmo.</p>
          </div>
          <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <div className="date-selector-wrapper">
              <input
                  type="date"
                  value={dataSelecionada}
                  onChange={(e) => setDataSelecionada(e.target.value)}
                  className="date-input-field"
              />
            </div>
            <Button variant="outlined" size="sm" onClick={carregarConsultas} disabled={loading}>
              {loading ? 'Atualizando...' : '🔄 Sincronizar Agenda'}
            </Button>
            <Button
                variant="primary"
                size="sm"
                style={{ backgroundColor: 'var(--color-secondary)' }}
                onClick={onNovoAgendamento}
            >
              + Novo Agendamento
            </Button>
          </div>
        </header>

        {/* Cards de Métricas com Dados Dinâmicos Reais */}
        <section className="metrics-grid" style={{ marginBottom: '24px' }}>
          <div className="metric-card">
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span className="metric-label">CONSULTAS HOJE</span>
              <Badge variant="neutral">+{Math.max(0, totalConsultas - 6)} encaixes</Badge>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="metric-value">{totalConsultas}</span>
              <span style={{ fontSize: '1.4rem' }}>📅</span>
            </div>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Total agendado para o dia</span>
          </div>

          <div className="metric-card">
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span className="metric-label" style={{ color: 'var(--color-tertiary)' }}>CONFIRMADAS</span>
              <Badge variant="success">{porcentagemConfirmadas}% confirmado</Badge>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="metric-value" style={{ color: 'var(--color-tertiary)' }}>{confirmadas}</span>
              <span style={{ fontSize: '1.4rem' }}>✓</span>
            </div>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Presença garantida via WhatsApp</span>
          </div>

          <div className="metric-card" style={{ border: '1px solid #FCD4D4' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span className="metric-label" style={{ color: 'var(--color-danger)' }}>RISCO ALTO DE FALTA</span>
              <Badge variant="danger">Atenção imediata</Badge>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="metric-value" style={{ color: 'var(--color-danger)' }}>{altoRiscoCount}</span>
              <span style={{ fontSize: '1.4rem' }}>⚠️</span>
            </div>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-danger)' }}>Requer contato ativo da equipe</span>
          </div>

          <div className="metric-card">
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span className="metric-label">LEMBRETES PENDENTES</span>
              <Badge variant="warning">Aguardando envio</Badge>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="metric-value">{lembretesPendentes}</span>
              <span style={{ fontSize: '1.4rem' }}>💬</span>
            </div>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Disparos automáticos programados</span>
          </div>
        </section>

        {/* Grid: Tabela + Pacientes de Risco Lateral */}
        <div className="recepcao-dashboard-grid">
          <section className="table-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                    type="button"
                    className={`species-filter-btn ${filtroAba === 'todos' ? 'active' : ''}`}
                    onClick={() => setFiltroAba('todos')}
                >
                  Todos ({totalConsultas})
                </button>
                <button
                    type="button"
                    className={`species-filter-btn ${filtroAba === 'confirmados' ? 'active' : ''}`}
                    onClick={() => setFiltroAba('confirmados')}
                >
                  Confirmados ({confirmadas})
                </button>
                <button
                    type="button"
                    className={`species-filter-btn ${filtroAba === 'risco' ? 'active' : ''}`}
                    onClick={() => setFiltroAba('risco')}
                >
                  Alto Risco ({altoRiscoCount})
                </button>
              </div>

              <div style={{ width: '220px' }}>
                <Input
                    placeholder="Buscar por tutor ou pet..."
                    value={filtroTexto}
                    onChange={(e) => setFiltroTexto(e.target.value)}
                />
              </div>
            </div>

            <table className="panel-table">
              <thead>
              <tr>
                <th>HORÁRIO</th>
                <th>PET / TUTOR</th>
                <th>VETERINÁRIO</th>
                <th>STATUS</th>
                <th>RISCO</th>
                <th style={{ textAlign: 'right' }}>AÇÕES RÁPIDAS</th>
              </tr>
              </thead>
              <tbody>
              {consultasFiltradas.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <div style={{ fontWeight: 800 }}>
                        {new Date(c.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                      </div>
                      <div style={{ fontSize: '0.7rem', color: 'var(--color-neutral-muted)' }}>
                        {c.detalheChegada || 'No horário'}
                      </div>
                    </td>
                    <td>
                      <div className="pet-name-cell">{c.petNome}</div>
                      <div className="tutor-name-cell">{c.raca || 'Pet'} • Tutor: {c.tutorNome}</div>
                    </td>
                    <td>
                      <div style={{ fontWeight: 700, fontSize: '0.85rem' }}>{c.veterinarioNome || 'Dra. Ana Souza'}</div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>{c.sala || 'Sala 03'}</div>
                    </td>
                    <td>{renderStatusBadge(c.status)}</td>
                    <td>{renderRiscoBadge(c)}</td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px', alignItems: 'center' }}>
                        <Button variant="outlined" size="sm" onClick={() => abrirModalStatus(c)}>
                          Atualizar
                        </Button>
                        <button
                            type="button"
                            title="Enviar Lembrete"
                            onClick={() => abrirModalLembrete(c)}
                            style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontSize: '1.1rem' }}
                        >
                          ✉️
                        </button>
                      </div>
                    </td>
                  </tr>
              ))}
              </tbody>
            </table>

            <div style={{ marginTop: '20px', backgroundColor: 'var(--color-surface-soft)', padding: '14px 20px', borderRadius: 'var(--radius-lg)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.85rem' }}>
              🪑 <strong>Capacidade da Recepção:</strong> 4 de 12 assentos ocupados • Ambiente climatizado e calmo
            </span>
              <Badge variant="primary">Ocupação: 33%</Badge>
            </div>
          </section>

          <aside className="sidebar-risk-panel">
            <div className="risk-alert-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ color: 'var(--color-danger)', fontSize: '0.9rem' }}>⚠️ Atenção: risco alto</strong>
                <Badge variant="danger">{altoRiscoCount} pacientes</Badge>
              </div>
              <p style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)', margin: '6px 0 14px 0' }}>
                Contatos prioritários para prevenção de absenteísmo clínico e otimização de horário cirúrgico.
              </p>

              {altoRiscoList.map((item) => (
                  <div key={item.id} className="risk-patient-entry">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <strong style={{ fontSize: '0.85rem' }}>
                        {new Date(item.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })} • {item.petNome}
                      </strong>
                      <Badge variant="danger">Alto Risco</Badge>
                    </div>
                    <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>{item.raca || 'Pet'}</span>
                    <p style={{ fontSize: '0.75rem', color: 'var(--color-danger)', margin: 0, fontWeight: 700 }}>
                      Motivo: {item.predicaoNoShow?.motivo || 'Histórico de faltas sem aviso prévio.'}
                    </p>
                    <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>
                  Tutor: {item.tutorNome} • {item.tutorTelefone || '(11) 98765-4321'}
                </span>
                    <div style={{ display: 'flex', gap: '6px', marginTop: '6px' }}>
                      <Button
                          variant="inverted"
                          size="sm"
                          style={{ backgroundColor: 'var(--color-danger)', flex: 1 }}
                          onClick={() => window.open(`tel:${item.tutorTelefone || '11987654321'}`)}
                      >
                        📞 Ligar agora
                      </Button>
                      <Button
                          variant="outlined"
                          size="sm"
                          style={{ flex: 1 }}
                          onClick={() => window.open(`https://wa.me/55${(item.tutorTelefone || '11987654321').replace(/\D/g, '')}`, '_blank')}
                      >
                        💬 WhatsApp
                      </Button>
                    </div>
                  </div>
              ))}
            </div>
          </aside>
        </div>

        {modalStatusAberto && consultaSelecionada && (
            <div className="modal-overlay">
              <div className="modal-operacional-card">
                <h3 style={{ margin: '0 0 10px 0' }}>Atualizar Consulta: {consultaSelecionada.petNome}</h3>
                <p style={{ fontSize: '0.85rem', color: 'var(--color-neutral-muted)' }}>
                  Tutor: {consultaSelecionada.tutorNome} • Horário: {new Date(consultaSelecionada.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', margin: '20px 0' }}>
                  {[
                    { id: 'CONFIRMADA', label: '✓ Confirmar presença' },
                    { id: 'EM_ATENDIMENTO', label: '🩺 Iniciar atendimento' },
                    { id: 'NAO_COMPARECEU', label: '❌ Registrar não comparecimento (No-show)' },
                    { id: 'CANCELADA', label: '🚫 Cancelar consulta' }
                  ].map((st) => (
                      <label key={st.id} style={{ display: 'flex', gap: '8px', cursor: 'pointer', fontSize: '0.9rem' }}>
                        <input
                            type="radio"
                            name="status"
                            checked={novoStatus === st.id}
                            onChange={() => setNovoStatus(st.id)}
                        />
                        {st.label}
                      </label>
                  ))}
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                  <Button variant="outlined" size="sm" onClick={() => setModalStatusAberto(false)}>Voltar</Button>
                  <Button variant="primary" size="sm" onClick={() => handleAtualizarStatus(consultaSelecionada.id, novoStatus)}>
                    Salvar alteração
                  </Button>
                </div>
              </div>
            </div>
        )}

        <ModalEnviarLembrete
            isOpen={modalLembreteAberto}
            onClose={() => setModalLembreteAberto(false)}
            consulta={consultaSelecionada}
            onLembreteEnviado={() => carregarConsultas()}
        />
      </div>
  );
}