import React, { useState } from 'react';
import { Button, Badge } from '../common/UIComponents';

const CONSULTAS_BASE = [
    { id: 1, petNome: 'Thor', especie: 'Cachorro', raca: 'Golden Retriever', vetNome: 'Dra. Ana Souza', crmv: 'CRMV-SP 42.189', data: 'Qui, 22 de Outubro de 2026', hora: '14:30', sala: 'Unidade Jardins (Sala 03)', status: 'CONFIRMADA' },
    { id: 2, petNome: 'Luna', especie: 'Gato', raca: 'Siamês', vetNome: 'Dr. Vinícius Lima', crmv: 'CRMV-SP 38.450', data: 'Qui, 05 de Novembro de 2026', hora: '10:00', sala: 'Unidade Jardins (Sala 07)', status: 'AGENDADA' }
];

export default function TutorConsultas({ onAgendar, onVerDetalhes }) {
    const [consultas, setConsultas] = useState(CONSULTAS_BASE);
    const [consultaCancelar, setConsultaCancelar] = useState(null);
    const [motivo, setMotivo] = useState('');

    const handleConfirmarCancelamento = () => {
        if (!consultaCancelar) return;
        setConsultas((prev) =>
            prev.map((c) => (c.id === consultaCancelar.id ? { ...c, status: 'CANCELADA' } : c))
        );
        setConsultaCancelar(null);
    };

    return (
        <div className="tutor-page-wrapper">
            <div className="tutor-breadcrumb">
                <span>Início</span>
                <span>/</span>
                <strong>Consultas</strong>
            </div>

            <header className="section-head-flex" style={{ marginBottom: '24px' }}>
                <div>
                    <h1 className="tutor-main-title" style={{ fontSize: '1.9rem' }}>Minhas consultas</h1>
                    <p className="tutor-main-sub">Acompanhe seus agendamentos, histórico de atendimentos clínicos e gerencie remarcações com facilidade.</p>
                </div>
                <Button variant="primary" onClick={onAgendar}>
                    + Agendar consulta
                </Button>
            </header>

            <div className="tutor-consultas-table-card">
                <table className="panel-table">
                    <thead>
                    <tr>
                        <th>PET</th>
                        <th>VETERINÁRIO & ESPECIALIDADE</th>
                        <th>DATA E HORÁRIO</th>
                        <th>STATUS</th>
                        <th style={{ textAlign: 'right' }}>AÇÕES</th>
                    </tr>
                    </thead>
                    <tbody>
                    {consultas.map((c) => (
                        <tr key={c.id}>
                            <td>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                    <div className="pet-thumb-circle" style={{ width: 40, height: 40, fontSize: '1.4rem' }}>
                                        {c.especie === 'Gato' ? '🐈' : '🐕'}
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: 800 }}>{c.petNome} ●</div>
                                        <div style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>{c.especie} • {c.raca}</div>
                                    </div>
                                </div>
                            </td>
                            <td>
                                <strong>{c.vetNome}</strong>
                                <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>{c.crmv}</div>
                            </td>
                            <td>
                                <div>📅 {c.data}</div>
                                <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>🕐 {c.hora} • {c.sala}</div>
                            </td>
                            <td>
                                <Badge variant={c.status === 'CONFIRMADA' ? 'success' : c.status === 'CANCELADA' ? 'danger' : 'warning'}>
                                    ● {c.status}
                                </Badge>
                            </td>
                            <td style={{ textAlign: 'right' }}>
                                {c.status !== 'CANCELADA' ? (
                                    <>
                      <span style={{ marginRight: '12px', fontSize: '0.85rem', color: 'var(--color-primary)', fontWeight: 700, cursor: 'pointer' }} onClick={onAgendar}>
                        Remarcar
                      </span>
                                        <span style={{ fontSize: '0.85rem', color: 'var(--color-danger)', fontWeight: 700, cursor: 'pointer' }} onClick={() => setConsultaCancelar(c)}>
                        Cancelar
                      </span>
                                    </>
                                ) : (
                                    <span style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>Cancelada</span>
                                )}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            <div className="urgency-help-box">
                <div>
                    <h4 style={{ margin: '0 0 4px 0', fontWeight: 800 }}>Precisa de auxílio ou remarcação urgente?</h4>
                    <span style={{ fontSize: '0.82rem', color: 'var(--color-neutral-muted)' }}>
            Para cancelamentos ou reagendamentos com menos de 24 horas de antecedência, fale diretamente com nossa recepção pelo WhatsApp.
          </span>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <Button variant="outlined" size="md">📞 0800 738 2843</Button>
                    <Button variant="secondary" size="md">💬 WhatsApp da Clínica</Button>
                </div>
            </div>

            {consultaCancelar && (
                <div className="modal-overlay">
                    <div className="modal-cancel-card">
                        <div className="cancel-alert-icon-circle">⚠️</div>
                        <h2 className="cancel-modal-title">Cancelar consulta?</h2>
                        <p className="cancel-modal-desc">
                            Você está cancelando a consulta de <strong>{consultaCancelar.petNome}</strong> com a <strong>{consultaCancelar.vetNome}</strong> em {consultaCancelar.data}.
                        </p>

                        <div className="input-group" style={{ textAlign: 'left', marginBottom: '20px' }}>
                            <label className="input-label">MOTIVO DO CANCELAMENTO (OPCIONAL)</label>
                            <select className="select-custom" value={motivo} onChange={(e) => setMotivo(e.target.value)}>
                                <option value="">Selecione um motivo...</option>
                                <option value="imprevisto">Imprevisto pessoal ou familiar</option>
                                <option value="melhora">O animal apresentou melhora clínica</option>
                                <option value="reagendar">Desejo reagendar para outro dia</option>
                            </select>
                        </div>

                        <div className="cancel-modal-actions">
                            <Button variant="outlined" onClick={() => setConsultaCancelar(null)}>Voltar</Button>
                            <Button variant="inverted" style={{ backgroundColor: 'var(--color-danger)' }} onClick={handleConfirmarCancelamento}>
                                ✕ Sim, cancelar consulta
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}