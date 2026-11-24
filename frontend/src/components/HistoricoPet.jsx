import React, { useState, useEffect, useRef } from 'react';
import { Badge, Button } from './common/UIComponents';
import { listarPets, obterHistoricoPet, obterResumoHistoricoPet } from '../api/api';
import RegistroNoProntuario from './RegistroNoProntuario';

const STATUS_LABEL = {
  AGENDADA: { label: 'Agendada', variant: 'warning' },
  CONFIRMADA: { label: 'Confirmada', variant: 'success' },
  REALIZADA: { label: 'Realizada', variant: 'primary' },
  CANCELADA: { label: 'Cancelada', variant: 'danger' },
  NAO_COMPARECEU: { label: 'Não compareceu', variant: 'danger' },
};

const formatarDataHora = (valor) =>
  valor
    ? new Date(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
    : 'Data não informada';

function StatusBadge({ status }) {
  const info = STATUS_LABEL[status] || { label: status || 'Sem status', variant: 'neutral' };
  return <Badge variant={info.variant}>{info.label}</Badge>;
}

function RegistroClinico({ atendimento }) {
  const foiEditado =
    atendimento.dataAtualizacao &&
    atendimento.dataCriacao &&
    atendimento.dataAtualizacao !== atendimento.dataCriacao;

  return (
    <div className="historico-registro">
      <p className="historico-registro-texto">{atendimento.descricaoClinica}</p>
      <div className="historico-registro-meta">
        {atendimento.veterinarioNome && <span>👨‍⚕️ {atendimento.veterinarioNome}</span>}
        <span>Registrado em {formatarDataHora(atendimento.dataCriacao || atendimento.dataAtendimento)}</span>
        {foiEditado && <span>· Atualizado em {formatarDataHora(atendimento.dataAtualizacao)}</span>}
      </div>
    </div>
  );
}

export default function HistoricoPet({ onRegistrarAtendimento, conta }) {
  const [pets, setPets] = useState([]);
  const [petId, setPetId] = useState('');
  const [erroPets, setErroPets] = useState('');
  const [atualizacao, setAtualizacao] = useState(0);
  const [avisoSalvo, setAvisoSalvo] = useState('');

  const [historico, setHistorico] = useState(null);
  const [carregandoHistorico, setCarregandoHistorico] = useState(false);
  const [erroHistorico, setErroHistorico] = useState(null);

  const [resumo, setResumo] = useState(null);
  const [carregandoResumo, setCarregandoResumo] = useState(false);
  const [erroResumo, setErroResumo] = useState(false);

  const [filtroStatus, setFiltroStatus] = useState('');
  const historicoRef = useRef(null);

  useEffect(() => {
    let ativo = true;
    listarPets()
      .then((res) => { if (ativo) setPets(Array.isArray(res.data) ? res.data : []); })
      .catch(() => { if (ativo) setErroPets('Não foi possível carregar os pacientes.'); });
    return () => { ativo = false; };
  }, []);

  // Histórico (dados reais) e resumo (IA) são carregados de forma independente:
  // uma falha da IA nunca bloqueia o acesso aos registros.
  useEffect(() => {
    setHistorico(null);
    setResumo(null);
    setErroHistorico(null);
    setErroResumo(false);
    setFiltroStatus('');
    if (!petId) return;

    let cancelado = false;

    setCarregandoHistorico(true);
    obterHistoricoPet(petId)
      .then((res) => { if (!cancelado) setHistorico(res.data); })
      .catch((err) => {
        console.error('Erro ao buscar histórico do pet:', err);
        if (!cancelado) setErroHistorico('Não foi possível carregar o histórico deste pet.');
      })
      .finally(() => { if (!cancelado) setCarregandoHistorico(false); });

    setCarregandoResumo(true);
    obterResumoHistoricoPet(petId)
      .then((res) => { if (!cancelado) setResumo(res.data); })
      .catch((err) => {
        console.error('Erro ao gerar resumo do pet:', err);
        if (!cancelado) setErroResumo(true);
      })
      .finally(() => { if (!cancelado) setCarregandoResumo(false); });

    return () => { cancelado = true; };
  }, [petId, atualizacao]);

  const irParaHistorico = () => {
    historicoRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const consultas = historico?.consultas || [];
  const consultasFiltradas = filtroStatus
    ? consultas.filter((c) => c.status === filtroStatus)
    : consultas;
  const semConsulta = historico?.atendimentosSemConsulta || [];
  const totalAtendimentos =
    consultas.reduce((acc, c) => acc + (c.atendimentos?.length || 0), 0) + semConsulta.length;
  const agora = new Date();
  const ultimaRealizada = consultas.find(
    (c) => c.status === 'REALIZADA' || new Date(c.dataHora) <= agora
  );

  return (
    <div className="clinica-container">
      <div className="clinical-history">
        <header className="history-header">
          <div>
            <span className="clinic-eyebrow">PRONTUÁRIO CLÍNICO UNIFICADO</span>
            <h1 className="form-title" style={{ marginTop: '8px' }}>Histórico do Pet</h1>
            <p className="form-subtitle">
              Consulte o resumo clínico e o histórico completo de consultas e atendimentos.
            </p>
          </div>

          <div style={{ minWidth: '280px' }}>
            <label className="input-label" htmlFor="historico-pet" style={{ marginBottom: '6px', display: 'block' }}>
              PACIENTE
            </label>
            <select
              id="historico-pet"
              className="select-custom"
              value={petId}
              onChange={(e) => { setPetId(e.target.value); setAvisoSalvo(''); }}
            >
              <option value="">Selecione um pet...</option>
              {pets.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nome} ({p.especie}) - Tutor: {p.tutorNome}
                </option>
              ))}
            </select>
          </div>
          <Button variant="outlined" size="sm" onClick={() => window.print()}>🖨️ Imprimir</Button>
        </header>

        {erroPets && <p className="alert-box error" role="alert">{erroPets}</p>}
        {avisoSalvo && <p className="alert-box success" role="status">{avisoSalvo}</p>}
        {!petId ? (
          <div className="empty-state">Selecione um pet para visualizar o histórico.</div>
        ) : (
          <>
            {historico && (
              <div className="table-card clinical-patient-card">
                <div className="pet-thumb-circle" aria-hidden="true">{historico.especie?.toLowerCase() === 'gato' ? '🐈' : '🐕'}</div>
                <div>
                  <h2>{historico.petNome}</h2>
                  <p>{[historico.especie, historico.idade != null ? historico.idade + ' ano(s)' : null].filter(Boolean).join(' • ')}</p>
                </div>
                {historico.tutorNome && <strong>Tutor: {historico.tutorNome}</strong>}
              </div>
            )}

            {/* Itens críticos: dado do cadastro, nunca dependente da IA */}
            {historico?.itensCriticos && (
              <div className="alert-allergy-box critical-allergy-alert" role="note">
                <span>⚠️ ITENS CRÍTICOS:</span>
                <span>{historico.itensCriticos}</span>
                <Badge variant="danger" className="historico-origem-badge">Dado do cadastro</Badge>
              </div>
            )}

            <div className="recepcao-dashboard-grid">
            <div>
            {/* Resumo gerado por IA */}
            <div className={`ai-summary-card ${erroResumo || resumo?.usouFallback ? 'fallback' : 'active'}`}>
              <div className="ai-summary-header">
                <h3 className={`ai-summary-title ${erroResumo || resumo?.usouFallback ? 'fallback' : 'active'}`}>
                  {erroResumo
                    ? '⚠️ Resumo clínico indisponível'
                    : resumo?.usouFallback
                      ? '⚠️ Resumo automático (sem IA)'
                      : '✨ Resumo clínico gerado por IA'}
                </h3>
                <Badge variant={erroResumo || resumo?.usouFallback ? 'warning' : 'success'}>
                  {erroResumo ? 'Indisponível' : resumo?.usouFallback ? 'Modo fallback' : 'Texto gerado por IA'}
                </Badge>
              </div>
              <p className="ai-summary-text">
                {carregandoResumo
                  ? 'Gerando resumo clínico...'
                  : erroResumo
                    ? 'Não foi possível gerar o resumo agora. Os registros completos abaixo continuam disponíveis.'
                    : resumo?.resumo || 'Nenhum resumo disponível para este paciente.'}
              </p>
              <p className="historico-aviso-ia">
                Texto de apoio. Confirme sempre as informações nos registros do histórico completo.
              </p>
              <div style={{ marginTop: '16px' }}>
                <Button variant="secondary" size="sm" type="button" onClick={irParaHistorico}>
                  Ver histórico completo ↓
                </Button>
              </div>
            </div>

            {/* Histórico completo: apenas dados registrados */}
            <section ref={historicoRef} className="historico-secao">
              <div className="historico-secao-header">
                <h2 className="timeline-section-title" style={{ margin: 0 }}>Histórico de consultas</h2>
                <Badge variant="neutral">Dados registrados</Badge>
              </div>

              {carregandoHistorico ? (
                <div className="empty-state">Carregando histórico do paciente...</div>
              ) : erroHistorico ? (
                <div className="alert-box error" role="alert">✗ {erroHistorico}</div>
              ) : historico && (
                <>
                  <div className="historico-stats">
                    <div className="historico-stat">
                      <span className="historico-stat-valor">{consultas.length}</span>
                      <span className="historico-stat-label">Consultas</span>
                    </div>
                    <div className="historico-stat">
                      <span className="historico-stat-valor">{totalAtendimentos}</span>
                      <span className="historico-stat-label">Atendimentos registrados</span>
                    </div>
                    <div className="historico-stat">
                      <span className="historico-stat-valor">
                        {ultimaRealizada ? new Date(ultimaRealizada.dataHora).toLocaleDateString('pt-BR') : '—'}
                      </span>
                      <span className="historico-stat-label">Última consulta</span>
                    </div>
                  </div>

                  {consultas.length > 0 && (
                    <div className="historico-filtro">
                      <label className="input-label" htmlFor="filtro-status">Filtrar por status</label>
                      <select
                        id="filtro-status"
                        className="select-custom"
                        value={filtroStatus}
                        onChange={(e) => setFiltroStatus(e.target.value)}
                      >
                        <option value="">Todos</option>
                        {Object.entries(STATUS_LABEL).map(([valor, { label }]) => (
                          <option key={valor} value={valor}>{label}</option>
                        ))}
                      </select>
                    </div>
                  )}

                  {consultas.length === 0 ? (
                    <div className="empty-state" style={{ padding: '24px' }}>
                      Nenhuma consulta registrada para este pet.
                    </div>
                  ) : consultasFiltradas.length === 0 ? (
                    <div className="empty-state" style={{ padding: '24px' }}>
                      Nenhuma consulta com o status selecionado.
                    </div>
                  ) : (
                    <div className="vet-timeline-wrap">
                      {consultasFiltradas.map((consulta) => (
                        <div key={consulta.consultaId} className="timeline-item vet-card-entry">
                          <div className="historico-consulta-header">
                            <div className="timeline-date">{formatarDataHora(consulta.dataHora)}</div>
                            <StatusBadge status={consulta.status} />
                          </div>
                          {consulta.veterinarioNome && (
                            <div className="historico-consulta-vet">Veterinário: {consulta.veterinarioNome}</div>
                          )}
                          {consulta.atendimentos?.length > 0 ? (
                            consulta.atendimentos.map((a) => <RegistroClinico key={a.id} atendimento={a} />)
                          ) : (
                            <p className="historico-sem-registro">
                              Nenhum atendimento clínico registrado nesta consulta.
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {semConsulta.length > 0 && (
                    <>
                      <h3 className="historico-subtitulo">Registros sem consulta vinculada</h3>
                      <div className="vet-timeline-wrap">
                        {semConsulta.map((a) => (
                          <div key={a.id} className="timeline-item vet-card-entry">
                            <div className="timeline-date">{formatarDataHora(a.dataAtendimento)}</div>
                            <RegistroClinico atendimento={a} />
                          </div>
                        ))}
                      </div>
                    </>
                  )}
                </>
              )}
            </section>
            </div>
            <aside className="table-card clinical-record-actions">
              {conta?.veterinarioId && historico ? <RegistroNoProntuario key={petId}
                petId={Number(petId)} petNome={historico.petNome} veterinarioId={conta.veterinarioId}
                consultas={consultas} onSalvo={() => { setAvisoSalvo('Atendimento registrado no prontuário.'); setAtualizacao(a => a + 1); }} />
                : <><h2 style={{ fontSize: '1.1rem' }}>Registrar atendimento</h2>
                  <p>Selecione um paciente para registrar a evolução clínica.</p></>}
              {onRegistrarAtendimento && <Button variant="outlined" onClick={onRegistrarAtendimento}>Abrir gestão de atendimentos →</Button>}
              <p className="historico-aviso-ia">Os alertas críticos vêm do cadastro do paciente e permanecem visíveis quando a IA está indisponível.</p>
            </aside>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
