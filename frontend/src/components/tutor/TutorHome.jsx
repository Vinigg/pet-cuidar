import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Button, Badge } from '../common/UIComponents';

export default function TutorHome({ onNavegar }) {
    const [pets, setPets] = useState([]);
    const [proximaConsulta, setProximaConsulta] = useState(null);

    useEffect(() => {
        async function carregar() {
            try {
                const [resPets, resCons] = await Promise.all([
                    axios.get('http://localhost:8080/pets/opcoes'),
                    axios.get(`http://localhost:8080/consultas?data=${new Date().toISOString().split('T')[0]}`)
                ]);
                setPets(resPets.data || []);
                if (resCons.data?.length > 0) {
                    setProximaConsulta(resCons.data[0]);
                }
            } catch {
                // Fallback para visualização imediata do protótipo
                setPets([
                    { petId: 1, petNome: 'Thor', especie: 'Cachorro', raca: 'Golden Retriever', idade: 4 },
                    { petId: 2, petNome: 'Luna', especie: 'Gato', raca: 'Siamês Tradicional', idade: 2 }
                ]);
            }
        }
        carregar();
    }, []);

    return (
        <div className="tutor-page-wrapper">
            <header className="tutor-dash-head">
                <div>
                    <span className="tutor-pill-tag">● Área do Tutor</span>
                    <h1 className="tutor-main-title">Olá, Mariana! 👋</h1>
                    <p className="tutor-main-sub">Veja o resumo de saúde e os próximos compromissos de seus companheiros.</p>
                </div>

                <div className="active-pets-pill">
                    <span style={{ fontSize: '1.2rem' }}>🐕🐈</span>
                    <div>
                        <span style={{ fontSize: '0.68rem', fontWeight: 700, color: 'var(--color-neutral-muted)', display: 'block' }}>COMPANHEIROS</span>
                        <span style={{ fontSize: '0.9rem', fontWeight: 800 }}>{pets.length || 2} pets ativos</span>
                    </div>
                </div>
            </header>

            <div className="tutor-home-grid">
                <div>
                    <section className="next-consult-banner-wrap">
                        <div className="next-consult-header">
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <span style={{ fontSize: '1.1rem' }}>📅</span>
                                <h2 style={{ fontSize: '1.1rem', fontWeight: 800, margin: 0 }}>Próxima consulta</h2>
                            </div>
                            <Badge variant="success">● Confirmada</Badge>
                        </div>

                        <div className="next-consult-pet-row">
                            <div className="pet-avatar-large">🐕</div>
                            <div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800 }}>
                                        {proximaConsulta?.petNome || 'Thor'}
                                    </h3>
                                    <Badge variant="neutral">Cachorro</Badge>
                                </div>
                                <p style={{ margin: '4px 0 0 0', fontWeight: 700, color: 'var(--color-primary)', fontSize: '0.92rem' }}>
                                    Consulta de rotina & Avaliação clínica
                                </p>
                                <span style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>
                  🩺 {proximaConsulta?.veterinarioNome || 'Dra. Ana Souza'} • Clínica Geral & Dermatologia
                </span>
                            </div>
                        </div>

                        <div className="next-consult-details-strip">
                            <div className="strip-item-col">
                                <span className="strip-item-label">DATA & HORÁRIO</span>
                                <span className="strip-item-val">
                  {proximaConsulta?.dataHora
                      ? new Date(proximaConsulta.dataHora).toLocaleString('pt-BR')
                      : 'Qui, 22 de Outubro de 2026, 14:30'}
                </span>
                            </div>
                            <div className="strip-item-col">
                                <span className="strip-item-label">LOCAL DE ATENDIMENTO</span>
                                <span className="strip-item-val">Unidade Jardins — Sala 03</span>
                            </div>
                        </div>

                        <div className="next-consult-footer-row">
              <span style={{ fontSize: '0.8rem', color: 'var(--color-tertiary)', fontWeight: 700 }}>
                ✓ Lembrete via WhatsApp ativado
              </span>
                            <div style={{ display: 'flex', gap: '10px' }}>
                                <Button variant="outlined" size="sm" onClick={() => onNavegar('agendar-consulta')}>
                                    Remarcar
                                </Button>
                                <Button variant="primary" size="sm" onClick={() => onNavegar('detalhes-consulta')}>
                                    👁️ Ver detalhes
                                </Button>
                            </div>
                        </div>
                    </section>

                    <section>
                        <div className="section-head-flex">
                            <h2 className="section-head-title">🐾 Meus pets</h2>
                            <span className="link-all-pets" onClick={() => onNavegar('meus-pets')}>
                Gerenciar todos ({pets.length || 2}) →
              </span>
                        </div>

                        <div className="pets-gallery-grid">
                            <div className="pet-profile-card">
                                <div style={{ height: 130, backgroundColor: '#E8F3F1', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '3.5rem' }}>
                                    🐕
                                </div>
                                <div className="pet-card-content">
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <h3 style={{ margin: 0, fontWeight: 800 }}>Thor</h3>
                                        <Badge variant="neutral">4 anos</Badge>
                                    </div>
                                    <span style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>Golden Retriever</span>
                                    <div style={{ fontSize: '0.78rem', color: 'var(--color-tertiary)', fontWeight: 700 }}>
                                        💉 Vacina V10 em dia
                                    </div>
                                    <Button variant="outlined" size="sm" onClick={() => onNavegar('detalhes-consulta')}>
                                        Ver prontuário →
                                    </Button>
                                </div>
                            </div>

                            <div className="pet-profile-card">
                                <div style={{ height: 130, backgroundColor: '#FFF5F0', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '3.5rem' }}>
                                    🐈
                                </div>
                                <div className="pet-card-content">
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <h3 style={{ margin: 0, fontWeight: 800 }}>Luna</h3>
                                        <Badge variant="neutral">2 anos</Badge>
                                    </div>
                                    <span style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>Siamês Tradicional</span>
                                    <div style={{ fontSize: '0.78rem', color: 'var(--color-secondary)', fontWeight: 700 }}>
                                        💊 Vermífugo: Dezembro
                                    </div>
                                    <Button variant="outlined" size="sm" onClick={() => onNavegar('detalhes-consulta')}>
                                        Ver prontuário →
                                    </Button>
                                </div>
                            </div>

                            <div className="add-pet-dash-card" onClick={() => onNavegar('cadastrar-pet')}>
                                <span className="add-pet-plus-circle">+</span>
                                <span style={{ fontWeight: 800, fontSize: '0.95rem' }}>+ Adicionar pet</span>
                                <span style={{ fontSize: '0.78rem', color: 'var(--color-neutral-muted)' }}>Cadastre cão, gato ou outro pet</span>
                            </div>
                        </div>
                    </section>
                </div>

                <aside style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    <div className="cta-agendamento-dark-card">
                        <div>
                            <span className="cta-tag-pill">🛡️ Agendamento Fácil</span>
                            <h2 className="cta-dark-title">Precisa de atendimento?</h2>
                            <p className="cta-dark-desc">
                                Agende consultas presenciais ou teleorientações com especialistas em poucos cliques.
                            </p>
                        </div>
                        <div>
                            <Button
                                variant="inverted"
                                size="lg"
                                style={{ width: '100%', backgroundColor: '#FFFFFF', color: 'var(--color-primary)' }}
                                onClick={() => onNavegar('agendar-consulta')}
                            >
                                📅 Agendar consulta
                            </Button>
                            <div style={{ textAlign: 'center', marginTop: '14px', fontSize: '0.8rem', opacity: 0.9 }}>
                                📞 Emergência 24h: 0800 738 2843
                            </div>
                        </div>
                    </div>

                    <div className="tutor-consultas-table-card" style={{ padding: '20px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                            <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 800 }}>🕐 Últimas consultas</h3>
                            <span style={{ fontSize: '0.78rem', color: 'var(--color-primary)', fontWeight: 700, cursor: 'pointer' }} onClick={() => onNavegar('consultas')}>
                Ver histórico
              </span>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                            <div style={{ borderBottom: '1px solid var(--color-surface-soft)', paddingBottom: '10px' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                    <strong>Luna</strong>
                                    <Badge variant="neutral">Realizada</Badge>
                                </div>
                                <div style={{ fontSize: '0.8rem', color: 'var(--color-primary)', fontWeight: 600 }}>Consulta Dermatológica</div>
                                <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>Dr. Carlos Mendes • 14/09/2026</span>
                            </div>

                            <div>
                                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                    <strong>Thor</strong>
                                    <Badge variant="neutral">Realizada</Badge>
                                </div>
                                <div style={{ fontSize: '0.8rem', color: 'var(--color-primary)', fontWeight: 600 }}>Vacina Antirrábica</div>
                                <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>Dra. Ana Souza • 02/08/2026</span>
                            </div>
                        </div>
                    </div>
                </aside>
            </div>
        </div>
    );
}