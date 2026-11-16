import React, { useState, useEffect, useCallback } from 'react';
import { api, listarConsultasDoDia, obterPredicaoNoShow } from '../api/api';
import { Button, Input, Badge } from './common/UIComponents';
import ModalEnviarLembrete from './ModalEnviarLembrete';

// Consultas que ainda podem faltar: so faz sentido pedir score para elas.
const STATUS_COM_PREDICAO = ['AGENDADA', 'CONFIRMADA'];

const ROTULOS_RISCO = {
  ALTO: { variant: 'danger', texto: 'Alto' },
  MEDIO: { variant: 'warning', texto: 'Médio' },
  BAIXO: { variant: 'success', texto: 'Baixo' },
};

export default function PainelRecepcionista() {
  const [dataSelecionada, setDataSelecionada] = useState(new Date().toISOString().split('T')[0]);
  const [consultas, setConsultas] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filtroTexto, setFiltroTexto] = useState('');
  // { [consultaId]: PredicaoNoShowDTO | 'carregando' | 'erro' }
  const [predicoes, setPredicoes] = useState({});

  const [modalLembreteAberto, setModalLembreteAberto] = useState(false);
  const [consultaSelecionada, setConsultaSelecionada] = useState(null);

  // Busca o score de cada consulta ativa. As chamadas sao sequenciais de
  // proposito: cada uma grava a predicao no SQLite, que nao lida bem com
  // escritas concorrentes. O volume (uma clinica, um dia) e pequeno.
  const carregarPredicoes = useCallback(async (lista, cancelado) => {
    const ativas = lista.filter(c => STATUS_COM_PREDICAO.includes(c.status));
    setPredicoes(Object.fromEntries(ativas.map(c => [c.id, 'carregando'])));
    for (const consulta of ativas) {
      if (cancelado()) return;
      let resultado;
      try {
        const response = await obterPredicaoNoShow(consulta.id);
        resultado = response.data;
      } catch (err) {
        resultado = 'erro';
      }
      if (cancelado()) return;
      setPredicoes(atual => ({ ...atual, [consulta.id]: resultado }));
    }
  }, []);

  const carregarConsultas = useCallback(async (cancelado = () => false) => {
    setLoading(true);
    try {
      const response = await listarConsultasDoDia(dataSelecionada);
      if (cancelado()) return;
      const lista = response.data || [];
      setConsultas(lista);
      carregarPredicoes(lista, cancelado);
    } catch (err) {
      console.error('Erro ao buscar consultas:', err);
    } finally {
      setLoading(false);
    }
  }, [dataSelecionada, carregarPredicoes]);

  useEffect(() => {
    // Ao trocar de data, descarta respostas atrasadas da data anterior.
    let cancelado = false;
    carregarConsultas(() => cancelado);
    return () => { cancelado = true; };
  }, [carregarConsultas]);

  const handleAtualizarStatus = async (consultaId, novoStatus) => {
    try {
      await api.patch(`/consultas/${consultaId}/status`, {
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

  // A categoria vem pronta do back-end/modelo (BAIXO < 0.25 <= MEDIO < 0.6 <= ALTO):
  // o front nao recalcula faixas, para nunca divergir do que o modelo decidiu.
  const renderRiscoBadge = (consulta) => {
    if (!STATUS_COM_PREDICAO.includes(consulta.status)) {
      return <Badge variant="neutral">—</Badge>;
    }
    const predicao = predicoes[consulta.id];
    if (!predicao || predicao === 'carregando') {
      return <Badge variant="neutral">Calculando...</Badge>;
    }
    const rotulo = predicao !== 'erro' && ROTULOS_RISCO[predicao.categoria];
    if (!rotulo || predicao.score == null) {
      // INDISPONIVEL, erro de rede ou resposta inesperada: nunca exibir como "Baixo".
      return (
          <Badge
              variant="neutral"
              title={predicao.motivo || 'Score indisponível no momento; confirme a presença normalmente.'}
          >
            Indisponível
          </Badge>
      );
    }
    return (
        <Badge variant={rotulo.variant} title={predicao.motivo}>
          ● {rotulo.texto} ({Math.round(predicao.score * 100)}%)
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
            <Button variant="outlined" size="sm" onClick={() => carregarConsultas()}>
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
                      <td>{renderRiscoBadge(c)}</td>
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