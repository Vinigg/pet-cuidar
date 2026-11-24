import React, { useCallback, useEffect, useRef, useState } from 'react';
import { listarConsultasDoDia, obterEstadoLembrete, dispararLembrete } from '../api/api';
import { dataLocal } from '../utils/datas';
import { Button, Badge } from './common/UIComponents';
import ModalEnviarLembrete from './ModalEnviarLembrete';
import { dataHora } from './tutor/TutorPresentation';

const ROTULOS = { NAO_GERADO: 'Não gerado', PENDENTE: 'Pendente', ENVIADO: 'Enviado', FALHOU: 'Falhou' };
export default function LembretesRecepcao() {
    const [data, setData] = useState(dataLocal);
    const [fila, setFila] = useState([]);
    const [carregando, setCarregando] = useState(false);
    const [erro, setErro] = useState('');
    const [mensagem, setMensagem] = useState('');
    const [consulta, setConsulta] = useState(null);
    const [enviando, setEnviando] = useState(false);
    const geracao = useRef(0);
    const lote = useRef(false);
    const montado = useRef(true);
    useEffect(() => {
        montado.current = true;
        return () => { montado.current = false; };
    }, []);
    const carregar = useCallback(async () => {
        const atual = ++geracao.current;
        setCarregando(true); setErro(''); setFila([]);
        try {
            const resposta = await listarConsultasDoDia(data);
            const lista = await Promise.all(resposta.data.map(async c => {
                try { const estado = await obterEstadoLembrete(c.id); return { ...c, lembrete: estado.data }; }
                catch { return { ...c, lembrete: { status: 'INDISPONIVEL' } }; }
            }));
            if (atual === geracao.current) setFila(lista);
        } catch { if (atual === geracao.current) setErro('Não foi possível carregar os lembretes.'); }
        finally { if (atual === geracao.current) setCarregando(false); }
    }, [data]);
    useEffect(() => { carregar(); return () => { geracao.current += 1; }; }, [carregar]);
    const elegiveis = fila.filter(c => ['AGENDADA', 'CONFIRMADA'].includes(c.status) && ['PENDENTE', 'NAO_GERADO'].includes(c.lembrete.status));
    const enviarLote = async () => {
        if (lote.current) return;
        lote.current = true; setEnviando(true); setMensagem(''); setErro('');
        let enviados = 0, falhas = 0;
        for (const item of elegiveis) {
            if (!montado.current) break;
            try { const resposta = await dispararLembrete(item.id); if (resposta.data.status === 'ENVIADO') enviados += 1; else falhas += 1; }
            catch { falhas += 1; }
        }
        if (!montado.current) return;
        setMensagem(`${enviados} lembrete(s) confirmado(s) pelo servidor; ${falhas} falha(s).`);
        await carregar();
        lote.current = false; setEnviando(false);
    };
    return <div className="clinica-container">
        <header className="panel-header"><div><span className="clinic-eyebrow">RECEPÇÃO • NOTIFICAÇÕES PREVENTIVAS</span><h1 className="tutor-main-title">Gestão e Envio de Lembretes</h1><p className="tutor-main-sub">Consulte o estado registrado e envie lembretes por e-mail.</p></div>
            <div className="clinic-toolbar"><label>Data das consultas <input type="date" className="select-custom" value={data} disabled={enviando} onChange={e => { if (e.target.value) { setData(e.target.value); setMensagem(''); } }} /></label><Button disabled={carregando || enviando || !elegiveis.length} onClick={enviarLote}>{enviando ? 'Enviando...' : 'Disparo em lote'}</Button><Button variant="outlined" disabled={carregando || enviando} onClick={carregar}>Atualizar</Button></div>
        </header>
        {erro && <p className="alert-box error" role="alert">{erro}</p>}{mensagem && <p role="status">{mensagem}</p>}
        <div className="metrics-grid">{[['ENVIADOS', 'ENVIADO'], ['PENDENTES', 'PENDENTE'], ['FALHAS DE ENVIO', 'FALHOU'], ['NÃO GERADOS', 'NAO_GERADO']].map(([nome, status]) => <div className="metric-card" key={status}><span className="metric-label">{nome}</span><span className="metric-value">{fila.filter(c => c.lembrete.status === status).length}</span></div>)}</div>
        {carregando ? <p role="status">Carregando lembretes...</p> : <section className="table-card clinic-table-scroll">
            {!fila.length ? <p className="empty-state">Nenhuma consulta nesta data.</p> : <table className="panel-table"><thead><tr><th>CONSULTA / PET</th><th>TUTOR</th><th>CANAL</th><th>STATUS DO ENVIO</th><th>AÇÕES</th></tr></thead><tbody>{fila.map(c => <tr key={c.id}><td><strong>{c.petNome}</strong><div>{dataHora(c.dataHora)} • {c.veterinarioNome}</div></td><td>{c.tutorNome}</td><td>E-mail</td><td><Badge variant={c.lembrete.status === 'ENVIADO' ? 'success' : c.lembrete.status === 'FALHOU' ? 'danger' : 'neutral'}>{ROTULOS[c.lembrete.status] || 'Indisponível'}</Badge>{c.lembrete.ultimoErro && <p>{c.lembrete.ultimoErro}</p>}</td><td><Button size="sm" variant="outlined" disabled={enviando} onClick={() => setConsulta(c)}>Ver mensagem</Button></td></tr>)}</tbody></table>}
        </section>}
        {consulta && <ModalEnviarLembrete isOpen consulta={consulta} resultadoInicial={consulta.lembrete}
            permitirEnvio={['AGENDADA', 'CONFIRMADA'].includes(consulta.status) && ['NAO_GERADO', 'PENDENTE'].includes(consulta.lembrete.status)}
            onClose={() => setConsulta(null)} onLembreteEnviado={() => { setConsulta(null); carregar(); }} />}
    </div>;
}
