import React from 'react';
import { Button } from '../common/UIComponents';
import { ResumoConsulta } from './TutorPresentation';

export default function ConsultaAgendadaSucesso({ dados, onVerConsultas, onVoltarInicio }) {
    return <div className="tutor-page-wrapper"><div className="booking-success-wrapper">
        <div className="success-illustration-hero" aria-hidden="true">🐾 ✓</div>
        <span className="tutor-pill-tag">AGENDAMENTO CONCLUÍDO</span>
        <h1 className="tutor-main-title">Consulta agendada!</h1>
        <p className="tutor-main-sub">O agendamento abaixo foi registrado pela clínica.</p>
        <ResumoConsulta consulta={dados} />
        <div className="restored-action-stack"><Button size="lg" onClick={onVerConsultas}>Ver minhas consultas</Button><Button variant="outlined" onClick={onVoltarInicio}>Voltar ao início</Button></div>
    </div></div>;
}
