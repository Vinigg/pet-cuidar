import React, { useEffect, useMemo, useState } from 'react';
import { listarConsultasDoDia } from '../api/api';
import { Button } from './common/UIComponents';
import { diasDaSemana } from '../utils/datas';
import { StatusConsulta } from './tutor/TutorPresentation';

export default function AgendaSemanal({ onNovaConsulta }) {
    const [offset, setOffset] = useState(0);
    const [atualizacao, setAtualizacao] = useState(0);
    const [consultas, setConsultas] = useState([]);
    const [carregando, setCarregando] = useState(true);
    const [erro, setErro] = useState('');
    const [veterinario, setVeterinario] = useState('');
    const dias = useMemo(() => diasDaSemana(offset), [offset]);
    useEffect(() => {
        let ativo = true;
        setCarregando(true); setErro(''); setConsultas([]);
        Promise.all(dias.map(d => listarConsultasDoDia(d.iso))).then(respostas => {
            if (ativo) {
                const lista = respostas.flatMap(r => r.data);
                setConsultas(lista);
                setVeterinario(anterior => lista.some(c => String(c.veterinarioId) === anterior) ? anterior : '');
            }
        }).catch(() => { if (ativo) setErro('Não foi possível carregar a agenda semanal.'); })
            .finally(() => { if (ativo) setCarregando(false); });
        return () => { ativo = false; };
    }, [dias, atualizacao]);
    const profissionais = [...new Map(consultas.map(c => [String(c.veterinarioId), c.veterinarioNome])).entries()];
    const visiveis = consultas.filter(c => !veterinario || String(c.veterinarioId) === veterinario);
    const horarios = [...new Set([...Array.from({ length: 24 }, (_, i) => `${String(8 + Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`), ...visiveis.map(c => c.dataHora.slice(11, 16))])].sort();
    return <div className="clinica-container">
        <header className="panel-header"><div><span className="clinic-eyebrow">RECEPÇÃO • AGENDA SEMANAL</span><h1 className="tutor-main-title">Agenda da Clínica</h1><p className="tutor-main-sub">Consultas registradas para a semana.</p></div><div className="clinic-toolbar">
            <Button variant="outlined" size="sm" onClick={() => setOffset(o => o - 1)} aria-label="Semana anterior">◀</Button><span>{dias[0].texto} a {dias[6].texto}</span><Button variant="outlined" size="sm" onClick={() => setOffset(o => o + 1)} aria-label="Próxima semana">▶</Button>
            <Button variant="outlined" size="sm" onClick={() => setOffset(0)}>Hoje</Button><Button size="sm" onClick={onNovaConsulta}>+ Nova consulta</Button>
        </div></header>
        <div className="clinic-toolbar"><label className="input-label">Veterinário <select className="select-custom" value={veterinario} onChange={e => setVeterinario(e.target.value)}><option value="">Todos</option>{profissionais.map(([id, nome]) => <option key={id} value={id}>{nome}</option>)}</select></label><Button variant="outlined" disabled={carregando} onClick={() => setAtualizacao(a => a + 1)}>Atualizar agenda</Button></div>
        {erro && <p className="alert-box error" role="alert">{erro}</p>}
        {carregando ? <p role="status">Carregando semana...</p> : !erro && <><p>Espaços vazios indicam ausência de consultas nesta visão. A disponibilidade para reservar é validada no agendamento.</p>
            {!visiveis.length && <p className="empty-state">Nenhuma consulta nesta semana e filtro.</p>}
            <div className="clinic-table-scroll"><div className="weekly-agenda-grid" style={{ minWidth: 900 }}>
                <div className="weekly-cell-header">Horário</div>{dias.map(d => <div className={`weekly-cell-header ${d.hoje ? 'today' : ''}`} key={d.iso}>{d.texto}{d.hoje && ' • HOJE'}</div>)}
                {horarios.map(h => <React.Fragment key={h}><div className="weekly-hour-cell">{h}</div>{dias.map(d => <div className="weekly-hour-cell" key={d.iso}>{visiveis.filter(c => c.dataHora.slice(0, 10) === d.iso && c.dataHora.slice(11, 16) === h).map(c => <article className="weekly-event-pill" key={c.id}><strong>{c.petNome}</strong><span>{c.veterinarioNome}</span><StatusConsulta status={c.status} /></article>)}</div>)}</React.Fragment>)}
            </div></div></>}
    </div>;
}
