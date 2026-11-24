import React from 'react';
import { Button } from '../common/UIComponents';

export default function ConsultaAgendadaSucesso({ dados, onVerConsultas, onVoltarInicio }) {
    return (
        <div className="tutor-page-wrapper">
            <div className="booking-success-wrapper">
                <h1 className="tutor-main-title">Consulta agendada!</h1>
                <p><strong>{dados.petNome}</strong> · {dados.veterinarioNome}</p>
                <p>{new Date(dados.dataHora).toLocaleString('pt-BR')}</p>
                <div className="form-actions-footer">
                    <Button variant="outlined" onClick={onVoltarInicio}>Voltar ao início</Button>
                    <Button onClick={onVerConsultas}>Ver minhas consultas</Button>
                </div>
            </div>
        </div>
    );
}
