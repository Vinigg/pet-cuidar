import React from 'react';
import { Badge, Button } from '../common/UIComponents';

export default function TutorConsultas({ consultas, onAgendar, onVerDetalhes }) {
    return (
        <div className="tutor-page-wrapper">
            <header className="tutor-dash-head">
                <h1 className="tutor-main-title">Minhas consultas</h1>
                <Button onClick={onAgendar}>Agendar consulta</Button>
            </header>
            {!consultas.length ? <p className="empty-state">Nenhuma consulta encontrada.</p> : (
                <div className="tutor-consultas-table-card">
                    <table className="panel-table">
                        <thead><tr><th>Pet</th><th>Veterinário</th><th>Data e horário</th><th>Status</th><th>Ações</th></tr></thead>
                        <tbody>
                            {consultas.map((consulta) => <tr key={consulta.id}>
                                <td>{consulta.petNome}</td>
                                <td>{consulta.veterinarioNome}</td>
                                <td>{new Date(consulta.dataHora).toLocaleString('pt-BR')}</td>
                                <td><Badge>{consulta.status}</Badge></td>
                                <td><Button variant="outlined" size="sm"
                                    onClick={() => onVerDetalhes(consulta.id)}>Ver detalhes</Button></td>
                            </tr>)}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
