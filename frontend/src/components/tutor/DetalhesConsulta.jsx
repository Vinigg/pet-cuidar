import React from 'react';
import { Button, Badge } from '../common/UIComponents';

export default function DetalhesConsulta({ onVoltar, onRemarcar, onCancelar }) {
    return (
        <div className="tutor-page-wrapper">
            <div className="tutor-breadcrumb">
                <span>Consultas</span>
                <span>/</span>
                <strong>Detalhes da consulta</strong>
            </div>

            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <div>
                    <h1 className="tutor-main-title" style={{ fontSize: '1.9rem' }}>Detalhes da consulta</h1>
                    <p className="tutor-main-sub">Acompanhe as informações, preparo e status do atendimento do seu pet.</p>
                </div>
                <Button variant="outlined" size="sm" onClick={onVoltar}>
                    ← Voltar para minhas consultas
                </Button>
            </header>

            <div className="booking-two-columns-layout">
                <div>
                    <div className="tutor-consultas-table-card" style={{ marginBottom: '24px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                                <div className="pet-thumb-circle" style={{ width: 64, height: 64, fontSize: '2.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>🐕</div>
                                <div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        <h2 style={{ margin: 0, fontSize: '1.4rem', fontWeight: 800 }}>Thor</h2>
                                        <span className="pet-card-tag-alert" style={{ position: 'static' }}>⚠️ Alergia crítica</span>
                                    </div>
                                    <p style={{ margin: '4px 0 0 0', fontSize: '0.82rem', color: 'var(--color-neutral-muted)' }}>
                                        Cachorro • Golden Retriever • 4 anos (32,4 kg) • Microchip: 981.098.102.433
                                    </p>
                                </div>
                            </div>
                            <Badge variant="success">● Confirmada</Badge>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginTop: '20px' }}>
                            <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                                <span className="strip-item-label">DATA E HORÁRIO</span>
                                <div style={{ fontWeight: 800, marginTop: '2px' }}>Quinta-feira, 22 de outubro de 2026 às 14:30</div>
                            </div>

                            <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                                <span className="strip-item-label">TIPO DE CONSULTA</span>
                                <div style={{ fontWeight: 800, marginTop: '2px' }}>Consulta de rotina (Check-up preventivo)</div>
                            </div>

                            <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                                <span className="strip-item-label">VETERINÁRIA RESPONSÁVEL</span>
                                <div style={{ fontWeight: 800, marginTop: '2px' }}>Dra. Ana Souza (CRMV-SP 48.912)</div>
                            </div>

                            <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                                <span className="strip-item-label">LOCAL DE ATENDIMENTO</span>
                                <div style={{ fontWeight: 800, marginTop: '2px' }}>Pet Cuidar - Unidade Jardins (Sala 03)</div>
                            </div>
                        </div>

                        <div style={{ marginTop: '28px' }}>
                            <span className="strip-item-label">STATUS DO ATENDIMENTO</span>
                            <div className="timeline-status-tracker">
                                <div className="timeline-tracker-bar">
                                    <div className="timeline-tracker-progress" style={{ width: '50%' }} />
                                </div>

                                <div className="timeline-tracker-step">
                                    <div className="timeline-tracker-dot done">✓</div>
                                    <strong style={{ fontSize: '0.78rem' }}>Agendada</strong>
                                    <span style={{ fontSize: '0.68rem', color: 'var(--color-neutral-muted)' }}>15/10 às 10:20</span>
                                </div>

                                <div className="timeline-tracker-step">
                                    <div className="timeline-tracker-dot current">📅</div>
                                    <strong style={{ fontSize: '0.78rem', color: 'var(--color-primary)' }}>Confirmada</strong>
                                    <span style={{ fontSize: '0.68rem', color: 'var(--color-primary)' }}>Pelo tutor</span>
                                </div>

                                <div className="timeline-tracker-step">
                                    <div className="timeline-tracker-dot upcoming">🕒</div>
                                    <strong style={{ fontSize: '0.78rem', color: 'var(--color-neutral-muted)' }}>Realizada</strong>
                                    <span style={{ fontSize: '0.68rem', color: 'var(--color-neutral-muted)' }}>Aguardando</span>
                                </div>
                            </div>
                        </div>

                        <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px 18px', borderRadius: 'var(--radius-md)', fontSize: '0.82rem', marginTop: '16px' }}>
                            <strong>📧 Lembrete de consulta sincronizado:</strong> Enviado para mariana.costa@email.com.
                        </div>

                        <div style={{ marginTop: '24px' }}>
                            <h3 style={{ margin: '0 0 12px 0', fontSize: '1rem', fontWeight: 800 }}>📋 Recomendações para a consulta</h3>
                            <div className="instruction-check-list">
                                <div className="instruction-check-item">
                                    <span style={{ color: 'var(--color-primary)', fontWeight: 800 }}>✓</span>
                                    <span><strong>Carteirinha de vacinação:</strong> Traga a versão física caso o histórico ainda não tenha sido totalmente digitalizado no portal.</span>
                                </div>
                                <div className="instruction-check-item">
                                    <span style={{ color: 'var(--color-primary)', fontWeight: 800 }}>✓</span>
                                    <span><strong>Jejum preventivo:</strong> Recomendamos jejum hídrico e alimentar brando de 2 horas caso seja necessária coleta de exames.</span>
                                </div>
                                <div className="instruction-check-item">
                                    <span style={{ color: 'var(--color-primary)', fontWeight: 800 }}>✓</span>
                                    <span><strong>Acessórios de contenção:</strong> Para maior segurança no lounge da clínica, mantenha Thor na guia com coleira confortável.</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <aside style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    <div className="tutor-consultas-table-card">
                        <h3 style={{ margin: '0 0 8px 0', fontSize: '1rem', fontWeight: 800 }}>Precisa mudar algo?</h3>
                        <p style={{ margin: '0 0 16px 0', fontSize: '0.82rem', color: 'var(--color-neutral-muted)' }}>
                            Gerencie o horário da consulta de forma rápida e autônoma.
                        </p>
                        <Button variant="primary" style={{ width: '100%', marginBottom: '10px' }} onClick={onRemarcar}>
                            📅 Remarcar consulta
                        </Button>
                        <Button variant="outlined" style={{ width: '100%', color: 'var(--color-danger)' }} onClick={onCancelar}>
                            ✕ Cancelar consulta
                        </Button>
                        <div style={{ marginTop: '14px', fontSize: '0.72rem', color: 'var(--color-neutral-muted)', lineHeight: 1.4 }}>
                            <strong>Política amigável:</strong> Cancelamentos e remarcações podem ser feitos até o horário agendado sem taxa de ausência.
                        </div>
                    </div>

                    <div className="tutor-consultas-table-card">
                        <h3 style={{ margin: '0 0 12px 0', fontSize: '1rem', fontWeight: 800 }}>Dúvidas ou emergência?</h3>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.85rem' }}>
                            <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '10px 14px', borderRadius: 'var(--radius-md)' }}>
                                📞 Recepção: (11) 3456-7890
                            </div>
                            <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '10px 14px', borderRadius: 'var(--radius-md)' }}>
                                💬 WhatsApp: (11) 98888-7777
                            </div>
                        </div>
                    </div>
                </aside>
            </div>
        </div>
    );
}