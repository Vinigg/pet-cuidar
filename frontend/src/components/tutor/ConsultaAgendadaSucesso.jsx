import React from 'react';
import { Button } from '../common/UIComponents';

export default function ConsultaAgendadaSucesso({ dados, onVerConsultas, onVoltarInicio }) {
    return (
        <div className="tutor-page-wrapper">
            <div className="booking-success-wrapper">
                <div className="success-illustration-hero">
                    🐶✨
                </div>

                <span className="tutor-pill-tag" style={{ marginBottom: '10px' }}>
          ● Agendamento finalizado com sucesso
        </span>

                <h1 className="tutor-main-title" style={{ fontSize: '2rem', margin: '8px 0' }}>
                    Consulta agendada!
                </h1>
                <p className="tutor-main-sub" style={{ marginBottom: '28px' }}>
                    Enviaremos lembretes detalhados e as orientações prévias antes do atendimento do seu pet.
                </p>

                <div style={{ backgroundColor: '#F0F7F6', borderRadius: 'var(--radius-lg)', padding: '24px', textAlign: 'left', marginBottom: '28px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                        <span style={{ fontWeight: 800, fontSize: '0.85rem' }}>📅 Resumo do agendamento</span>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-primary)', fontWeight: 800 }}>● AGENDADA</span>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', marginBottom: '14px' }}>
                        <div>
                            <span className="strip-item-label">PACIENTE</span>
                            <div style={{ fontWeight: 800 }}>{dados?.petNome || 'Thor'}</div>
                            <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Cachorro • Golden Retriever</span>
                        </div>
                        <div>
                            <span className="strip-item-label">PROFISSIONAL</span>
                            <div style={{ fontWeight: 800 }}>{dados?.vetNome || 'Dra. Ana Souza'}</div>
                            <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>CRMV-SP 42.189 • Dermatologia</span>
                        </div>
                    </div>

                    <div style={{ marginBottom: '14px' }}>
                        <span className="strip-item-label">DATA E HORÁRIO</span>
                        <div style={{ fontWeight: 800 }}>{dados?.dataHora || 'Quinta-feira, 22 de Outubro de 2026 às 14:30'}</div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Duração estimada: 45 minutos</span>
                    </div>

                    <div>
                        <span className="strip-item-label">UNIDADE E SALA</span>
                        <div style={{ fontWeight: 800 }}>Unidade Jardins — Sala 03 (Consultório Especializado)</div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Alameda dos Guaiós, 412 • Estacionamento conveniado no local</span>
                    </div>

                    <div style={{ marginTop: '16px', backgroundColor: '#FFFFFF', padding: '10px 14px', borderRadius: 'var(--radius-md)', fontSize: '0.78rem', color: 'var(--color-tertiary)', fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                        <span>💬 Lembrete via WhatsApp ativado para (11) 98765-4321</span>
                        <span>✓</span>
                    </div>
                </div>

                <Button variant="primary" size="lg" style={{ width: '100%' }} onClick={onVerConsultas}>
                    📅 Ver minhas consultas →
                </Button>

                <div style={{ marginTop: '14px' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--color-neutral-muted)', cursor: 'pointer', fontWeight: 600 }} onClick={onVoltarInicio}>
            ← Voltar ao início
          </span>
                </div>
            </div>
        </div>
    );
}