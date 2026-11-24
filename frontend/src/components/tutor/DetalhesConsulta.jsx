import React, { useState } from 'react';
import { Button } from '../common/UIComponents';
import { ConfirmarCancelamento, podeAlterar, ResumoConsulta, statusInfo } from './TutorPresentation';

export default function DetalhesConsulta({ consulta, pet, onVoltar, onRemarcar, onCancelar, cancelando, erro }) {
    const [confirmar, setConfirmar] = useState(false);
    return <div className="tutor-page-wrapper">
        <div className="tutor-breadcrumb"><span>Consultas</span><span>/</span><strong>Detalhes</strong></div>
        <header className="section-head-flex"><div><h1 className="tutor-main-title">Detalhes da consulta</h1><p className="tutor-main-sub">Informações e status do atendimento do seu pet.</p></div><Button variant="outlined" onClick={onVoltar}>Voltar às consultas</Button></header>
        <div className="booking-two-columns-layout">
            <div><ResumoConsulta consulta={consulta} pet={pet} />
                <section className="tutor-consultas-table-card restored-consult-card"><h2 className="section-head-title">Status do atendimento</h2>
                    <div className="restored-status-tracker"><div className="timeline-tracker-step"><span className="timeline-tracker-dot done">✓</span><strong>Agendamento registrado</strong></div>
                        <div className="timeline-tracker-step"><span className="timeline-tracker-dot current">●</span><strong>{statusInfo[consulta.status]?.[0] || consulta.status}</strong></div></div>
                    <p>O status acima corresponde ao registro atual da clínica.</p>
                </section>
            </div>
            <aside className="booking-summary-side-card"><h2 className="section-head-title">Gerenciar consulta</h2><p>Confira data, horário e profissional antes de fazer alterações.</p>
                {podeAlterar(consulta) ? <div className="restored-action-stack"><Button onClick={onRemarcar}>Remarcar</Button><Button variant="outlined" onClick={() => setConfirmar(true)}>Cancelar consulta</Button></div> : <p>Esta consulta não permite cancelamento ou remarcação.</p>}
                {erro && !confirmar && <p className="alert-box error" role="alert">{erro}</p>}
            </aside>
        </div>
        {confirmar && <ConfirmarCancelamento consulta={consulta} onFechar={() => setConfirmar(false)} onConfirmar={async () => { if (await onCancelar()) setConfirmar(false); }} cancelando={cancelando} erro={erro} />}
    </div>;
}
