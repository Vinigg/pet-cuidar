import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api, listarConsultasDoDia, obterPredicaoNoShow } from '../api/api';
import { Button, Input, Badge } from './common/UIComponents';
import ModalEnviarLembrete from './ModalEnviarLembrete';

const ATIVAS = ['AGENDADA', 'CONFIRMADA'];
const RISCOS = {
    BAIXO: { texto: 'Baixo', variant: 'success' },
    MEDIO: { texto: 'Médio', variant: 'warning' },
    ALTO: { texto: 'Alto', variant: 'danger' },
};
const STATUS = {
    AGENDADA: ['Agendada', 'neutral'], CONFIRMADA: ['Confirmada', 'success'],
    REALIZADA: ['Realizada', 'primary'], CANCELADA: ['Cancelada', 'danger'],
    NAO_COMPARECEU: ['Não compareceu', 'danger'],
};
const OPCOES_STATUS = [
    ['CONFIRMADA', '✓ Confirmar presença'], ['REALIZADA', '✓ Registrar conclusão'],
    ['NAO_COMPARECEU', 'Registrar não comparecimento (No-show)'], ['CANCELADA', 'Cancelar consulta'],
];
const hojeLocal = () => {
    const hoje = new Date();
    return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
};

export default function PainelRecepcionista({ onNovoAgendamento }) {
    const [dataSelecionada, setDataSelecionada] = useState(hojeLocal);
    const [consultas, setConsultas] = useState([]);
    const [predicoes, setPredicoes] = useState({});
    const [loading, setLoading] = useState(false);
    const [erro, setErro] = useState('');
    const [filtroTexto, setFiltroTexto] = useState('');
    const [filtroAba, setFiltroAba] = useState('todos');
    const [consultaSelecionada, setConsultaSelecionada] = useState(null);
    const [modalLembreteAberto, setModalLembreteAberto] = useState(false);
    const [modalStatusAberto, setModalStatusAberto] = useState(false);
    const [novoStatus, setNovoStatus] = useState('CONFIRMADA');
    const [salvando, setSalvando] = useState(false);
    const [erroStatus, setErroStatus] = useState('');
    const geracao = useRef(0);
    const mutacao = useRef(false);

    const carregarConsultas = useCallback(async () => {
        const atual = ++geracao.current;
        setLoading(true);
        setErro('');
        setConsultas([]);
        setPredicoes({});
        try {
            const { data } = await listarConsultasDoDia(dataSelecionada);
            if (atual !== geracao.current) return;
            const lista = Array.isArray(data) ? data : [];
            setConsultas(lista);
            setLoading(false);
            const ativas = lista.filter(c => ATIVAS.includes(c.status));
            setPredicoes(Object.fromEntries(ativas.map(c => [c.id, 'carregando'])));
            // Cada predição persiste no SQLite. Evita escritas paralelas desse painel.
            for (const consulta of ativas) {
                if (atual !== geracao.current) return;
                let predicao;
                try {
                    const resposta = await obterPredicaoNoShow(consulta.id);
                    predicao = resposta.data;
                } catch {
                    predicao = null;
                }
                if (atual !== geracao.current) return;
                setPredicoes(anteriores => ({ ...anteriores, [consulta.id]: predicao }));
            }
        } catch (e) {
            if (atual === geracao.current) setErro(e.response?.data?.mensagem || 'Não foi possível carregar a agenda.');
        } finally {
            if (atual === geracao.current) setLoading(false);
        }
    }, [dataSelecionada]);

    useEffect(() => {
        carregarConsultas();
        return () => { geracao.current += 1; };
    }, [carregarConsultas]);

    const altoRiscoList = consultas.filter(c => ATIVAS.includes(c.status) && predicoes[c.id]?.categoria === 'ALTO');
    const confirmadas = consultas.filter(c => c.status === 'CONFIRMADA').length;
    const pendentes = consultas.filter(c => c.status === 'AGENDADA').length;
    const realizadas = consultas.filter(c => c.status === 'REALIZADA').length;
    const consultasFiltradas = consultas.filter(c => {
        const busca = `${c.petNome || ''} ${c.tutorNome || ''}`.toLowerCase().includes(filtroTexto.toLowerCase());
        return busca && (filtroAba === 'todos' || (filtroAba === 'confirmados'
            ? c.status === 'CONFIRMADA' : ATIVAS.includes(c.status) && predicoes[c.id]?.categoria === 'ALTO'));
    });

    const renderRisco = (consulta) => {
        if (!ATIVAS.includes(consulta.status)) return <span>—</span>;
        const resultado = predicoes[consulta.id];
        if (resultado === 'carregando') return <Badge variant="neutral">Calculando…</Badge>;
        const risco = RISCOS[resultado?.categoria];
        if (!risco || !Number.isFinite(resultado?.score)) {
            return <Badge variant="neutral" title={resultado?.motivo}>Indisponível</Badge>;
        }
        return <Badge variant={risco.variant} title={resultado.motivo}>
            {risco.texto} ({Math.round(resultado.score * 100)}%)
        </Badge>;
    };

    const abrirLembrete = (consulta) => {
        setConsultaSelecionada(consulta);
        setModalLembreteAberto(true);
    };
    const abrirStatus = (consulta) => {
        setConsultaSelecionada(consulta);
        setNovoStatus(consulta.status === 'AGENDADA' ? 'CONFIRMADA' : 'REALIZADA');
        setErroStatus('');
        setModalStatusAberto(true);
    };
    const salvarStatus = async () => {
        if (mutacao.current) return;
        mutacao.current = true;
        setSalvando(true);
        setErroStatus('');
        try {
            await api.patch(`/consultas/${consultaSelecionada.id}/status`, { status: novoStatus });
            setModalStatusAberto(false);
            carregarConsultas();
        } catch (e) {
            setErroStatus(e.response?.data?.mensagem || 'Não foi possível atualizar o status.');
        } finally {
            mutacao.current = false;
            setSalvando(false);
        }
    };

    return (
        <div className="clinica-container">
            <header className="panel-header">
                <div>
                    <span className="clinic-eyebrow">● VISÃO OPERACIONAL DA CLÍNICA</span>
                    <h1 className="panel-title">Painel do Dia</h1>
                    <p className="panel-subtitle">Acompanhe consultas, confirmações e risco de falta.</p>
                </div>
                <div className="clinic-toolbar">
                    <label className="date-selector-wrapper">
                        <span className="input-label">Data da agenda</span>
                        <input type="date" value={dataSelecionada} required onChange={e => { if (e.target.value) setDataSelecionada(e.target.value); }} className="date-input-field" />
                    </label>
                    <Button variant="outlined" size="sm" onClick={() => carregarConsultas()} disabled={loading}>
                        {loading ? 'Atualizando...' : '🔄 Sincronizar Agenda'}
                    </Button>
                    {onNovoAgendamento && <Button size="sm" onClick={onNovoAgendamento}>+ Novo Agendamento</Button>}
                </div>
            </header>
            {erro && <div className="alert-box error" role="alert">{erro}</div>}
            <section className="metrics-grid">
                <div className="metric-card"><span className="metric-label">CONSULTAS DO DIA</span><span className="metric-value">{consultas.length}</span><span>Total agendado para a data</span></div>
                <div className="metric-card"><span className="metric-label">CONFIRMADAS</span><span className="metric-value">{confirmadas}</span><Badge variant="success">{consultas.length ? Math.round(confirmadas / consultas.length * 100) : 0}% confirmado</Badge></div>
                <div className="metric-card"><span className="metric-label">RISCO ALTO DE FALTA</span><span className="metric-value">{altoRiscoList.length}</span><span>Previsões disponíveis do serviço</span></div>
                <div className="metric-card"><span className="metric-label">AGUARDANDO CONFIRMAÇÃO</span><span className="metric-value">{pendentes}</span><span>{realizadas} consulta(s) realizada(s)</span></div>
            </section>
            <div className="recepcao-dashboard-grid">
                <section className="table-card">
                    <div className="clinic-toolbar">
                        <div className="clinic-toolbar">
                            {[['todos', `Todos (${consultas.length})`], ['confirmados', `Confirmados (${confirmadas})`], ['risco', `Alto Risco (${altoRiscoList.length})`]].map(([id, label]) => (
                                <button key={id} type="button" className={`species-filter-btn ${filtroAba === id ? 'active' : ''}`} onClick={() => setFiltroAba(id)}>{label}</button>
                            ))}
                        </div>
                        <Input aria-label="Buscar por tutor ou pet" placeholder="Buscar por tutor ou pet..." value={filtroTexto} onChange={e => setFiltroTexto(e.target.value)} />
                    </div>
                    {loading ? <div className="empty-state" role="status">Carregando agenda...</div> : consultasFiltradas.length === 0 ? (
                        <div className="empty-state">{erro ? 'Agenda indisponível.' : 'Nenhuma consulta encontrada para esta data e filtros.'}</div>
                    ) : <div className="clinic-table-scroll"><table className="panel-table">
                        <thead><tr><th>HORÁRIO</th><th>PET / TUTOR</th><th>VETERINÁRIO</th><th>STATUS</th><th>RISCO</th><th>AÇÕES RÁPIDAS</th></tr></thead>
                        <tbody>{consultasFiltradas.map(c => (
                            <tr key={c.id}>
                                <td><strong>{new Date(c.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</strong></td>
                                <td><div className="pet-name-cell">{c.petNome}</div><div className="tutor-name-cell">Tutor: {c.tutorNome}</div></td>
                                <td>{c.veterinarioNome || 'Não informado'}</td>
                                <td><Badge variant={STATUS[c.status]?.[1] || 'neutral'}>{STATUS[c.status]?.[0] || c.status}</Badge></td>
                                <td>{renderRisco(c)}</td>
                                <td>{ATIVAS.includes(c.status) && <div className="clinic-toolbar">
                                    <Button variant="outlined" size="sm" onClick={() => abrirStatus(c)}>Atualizar</Button>
                                    <Button variant="outlined" size="sm" onClick={() => abrirLembrete(c)} aria-label={`Enviar lembrete para ${c.petNome}`}>✉️</Button>
                                </div>}</td>
                            </tr>
                        ))}</tbody>
                    </table></div>}
                </section>
                <aside className="sidebar-risk-panel">
                    <div className="risk-alert-card">
                        <h2 style={{ fontSize: '1.1rem' }}>⚠️ Atenção ao risco de falta</h2>
                        <p>Use a previsão como apoio à decisão da equipe.</p>
                        {altoRiscoList.length === 0 && <p>Nenhuma consulta com risco alto disponível.</p>}
                        {altoRiscoList.map(c => <div key={c.id} className="risk-patient-entry">
                            <strong>{c.petNome}</strong><div className="tutor-name-cell">Tutor: {c.tutorNome}</div>
                            <p>{predicoes[c.id]?.motivo}</p>
                            <Button variant="outlined" size="sm" onClick={() => abrirLembrete(c)}>Enviar lembrete por e-mail</Button>
                        </div>)}
                    </div>
                </aside>
            </div>
            {modalStatusAberto && consultaSelecionada && <div className="modal-overlay">
                <div className="modal-operacional-card" role="dialog" aria-modal="true" aria-labelledby="titulo-status">
                    <h2 id="titulo-status">Atualizar Consulta: {consultaSelecionada.petNome}</h2>
                    <p>Tutor: {consultaSelecionada.tutorNome}</p>
                    <div className="clinic-status-options">{OPCOES_STATUS.filter(([id]) => id !== consultaSelecionada.status).map(([id, label]) => (
                        <label key={id}><input type="radio" name="status" value={id} checked={novoStatus === id} onChange={() => setNovoStatus(id)} disabled={salvando} /> {label}</label>
                    ))}</div>
                    {erroStatus && <p className="alert-box error" role="alert">{erroStatus}</p>}
                    <div className="modal-actions">
                        <Button variant="outlined" size="sm" onClick={() => setModalStatusAberto(false)} disabled={salvando}>Voltar</Button>
                        <Button size="sm" onClick={salvarStatus} disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar alteração'}</Button>
                    </div>
                </div>
            </div>}
            <ModalEnviarLembrete isOpen={modalLembreteAberto} onClose={() => setModalLembreteAberto(false)}
                consulta={consultaSelecionada} onLembreteEnviado={() => carregarConsultas()} />
        </div>
    );
}
