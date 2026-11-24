import React, { useState } from 'react';
import { Button } from '../common/UIComponents';
import { ConfirmarCancelamento, dataHora, podeAlterar, StatusConsulta } from './TutorPresentation';

export default function TutorConsultas({ consultas, onAgendar, onVerDetalhes, onRemarcar, onCancelar, cancelando, erro }) {
    const [selecionada, setSelecionada] = useState(null);
    const confirmar = async () => { if (await onCancelar(selecionada.id)) setSelecionada(null); };
    return <div className="tutor-page-wrapper">
        <div className="tutor-breadcrumb"><span>Início</span><span>/</span><strong>Consultas</strong></div>
        <header className="section-head-flex"><div><h1 className="tutor-main-title">Minhas consultas</h1><p className="tutor-main-sub">Acompanhe agendamentos e gerencie remarcações.</p></div><Button onClick={onAgendar}>Agendar consulta</Button></header>
        {!consultas.length ? <p className="empty-state">Nenhuma consulta encontrada.</p> : <div className="tutor-consultas-table-card clinic-table-scroll">
            <table className="panel-table"><thead><tr><th>Pet</th><th>Veterinário</th><th>Data e horário</th><th>Status</th><th>Ações</th></tr></thead>
                <tbody>{consultas.map(c => <tr key={c.id}><td><div className="restored-patient-heading"><span className="pet-thumb-circle" aria-hidden="true">🐾</span><strong>{c.petNome}</strong></div></td><td>{c.veterinarioNome}</td><td>{dataHora(c.dataHora)}</td><td><StatusConsulta status={c.status} /></td>
                    <td><div className="clinic-toolbar"><Button variant="outlined" size="sm" onClick={() => onVerDetalhes(c.id)}>Ver detalhes</Button>{podeAlterar(c) && <><Button variant="outlined" size="sm" onClick={() => onRemarcar(c.id)}>Remarcar</Button><Button variant="outlined" size="sm" onClick={() => setSelecionada(c)}>Cancelar</Button></>}</div></td></tr>)}</tbody>
            </table>
        </div>}
        {selecionada && <ConfirmarCancelamento consulta={selecionada} onFechar={() => setSelecionada(null)} onConfirmar={confirmar} cancelando={cancelando} erro={erro} />}
    </div>;
}
