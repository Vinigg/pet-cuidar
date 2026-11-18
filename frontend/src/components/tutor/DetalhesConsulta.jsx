import React, { useState } from 'react';
import { Badge, Button } from '../common/UIComponents';

export default function DetalhesConsulta({ consulta, onVoltar, onRemarcar, onCancelar, cancelando, erro }) {
    const [confirmar, setConfirmar] = useState(false);
    const ativa = ['AGENDADA', 'CONFIRMADA'].includes(consulta.status);

    return (
        <div className="tutor-page-wrapper">
            <div className="form-card">
                <h1 className="form-title">Detalhes da consulta</h1>
                <p><strong>{consulta.petNome}</strong> · {consulta.veterinarioNome}</p>
                <p>{new Date(consulta.dataHora).toLocaleString('pt-BR')} · <Badge>{consulta.status}</Badge></p>
                {erro && <p className="alert-box error" role="alert">{erro}</p>}
                {ativa && confirmar && <p>Confirma o cancelamento desta consulta?</p>}
                <div className="form-actions-footer">
                    <Button variant="outlined" onClick={onVoltar}>Voltar às consultas</Button>
                    {ativa && (confirmar ? (
                        <>
                            <Button variant="outlined" disabled={cancelando}
                                onClick={() => setConfirmar(false)}>Manter consulta</Button>
                            <Button disabled={cancelando} onClick={onCancelar}>Confirmar cancelamento</Button>
                        </>
                    ) : (
                        <>
                            <Button variant="outlined" onClick={() => setConfirmar(true)}>Cancelar consulta</Button>
                            <Button onClick={onRemarcar}>Remarcar</Button>
                        </>
                    ))}
                </div>
            </div>
        </div>
    );
}
