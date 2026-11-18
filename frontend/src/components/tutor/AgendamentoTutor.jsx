import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Button, Badge } from '../common/UIComponents';

const PETS_MOCK_FALLBACK = [
    { petId: 1, petNome: 'Thor', especie: 'Cachorro', raca: 'Golden Retriever', idade: 4 },
    { petId: 2, petNome: 'Luna', especie: 'Gato', raca: 'Siamês', idade: 2 }
];

export default function AgendamentoTutor({ onAvancarSucesso, onCancelar }) {
    const [pets, setPets] = useState(PETS_MOCK_FALLBACK);
    const [petSelecionado, setPetSelecionado] = useState(PETS_MOCK_FALLBACK[0]);
    const [vetSelecionado, setVetSelecionado] = useState({ id: 1, nome: 'Dra. Ana Souza' });

    // Controle Dinâmico de Mês e Ano
    const [dataAtual, setDataAtual] = useState(new Date(2026, 9, 1)); // Outubro 2026
    const [diaSelecionado, setDiaSelecionado] = useState(22);
    const [horarioEscolhido, setHorarioEscolhido] = useState('14:30');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        async function carregar() {
            try {
                const resP = await axios.get('http://localhost:8080/pets/opcoes');
                if (resP.data && resP.data.length > 0) {
                    setPets(resP.data);
                    setPetSelecionado(resP.data[0]);
                }
            } catch {
                setPets(PETS_MOCK_FALLBACK);
            }
        }
        carregar();
    }, []);

    const ano = dataAtual.getFullYear();
    const mes = dataAtual.getMonth();

    const mudarMes = (delta) => {
        setDataAtual(new Date(ano, mes + delta, 1));
    };

    const primeiroDiaSemana = new Date(ano, mes, 1).getDay();
    const totalDiasMes = new Date(ano, mes + 1, 0).getDate();
    const diasMes = Array.from({ length: totalDiasMes }, (_, i) => i + 1);

    const horariosDisponiveis = [
        { hora: '09:00', ocupado: false },
        { hora: '09:30', ocupado: false },
        { hora: '10:00', ocupado: false },
        { hora: '11:30', ocupado: true },
        { hora: '14:30', ocupado: false },
        { hora: '15:00', ocupado: false },
        { hora: '16:30', ocupado: false },
        { hora: '17:00', ocupado: true },
        { hora: '17:30', ocupado: false }
    ];

    const handleConfirmar = async () => {
        setLoading(true);
        const dataHoraStr = `${diaSelecionado}/${mes + 1}/${ano} às ${horarioEscolhido}`;

        try {
            await axios.post('http://localhost:8080/consultas', {
                petId: petSelecionado.petId || petSelecionado.id,
                veterinarioId: vetSelecionado.id,
                dataHora: `${ano}-${String(mes + 1).padStart(2, '0')}-${String(diaSelecionado).padStart(2, '0')}T${horarioEscolhido}:00`
            });
            onAvancarSucesso({
                petNome: petSelecionado.petNome || petSelecionado.nome,
                vetNome: vetSelecionado.nome,
                dataHora: dataHoraStr
            });
        } catch {
            onAvancarSucesso({
                petNome: petSelecionado.petNome || petSelecionado.nome,
                vetNome: vetSelecionado.nome,
                dataHora: dataHoraStr
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="tutor-page-wrapper">
            <div className="tutor-breadcrumb">
                <span>Início</span>
                <span>/</span>
                <span>Consultas</span>
                <span>/</span>
                <strong>Agendar consulta</strong>
            </div>

            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '28px' }}>
                <div>
                    <h1 className="tutor-main-title" style={{ fontSize: '1.8rem' }}>
                        Agendar consulta <span style={{ fontSize: '0.85rem', color: 'var(--color-primary)' }}>● Passo 1 de 2</span>
                    </h1>
                    <p className="tutor-main-sub">Selecione o pet, profissional e horário ideal para o atendimento clínico.</p>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>
                    🛡️ Agendamento Seguro (Confirmação instantânea)
                </div>
            </header>

            <div className="booking-two-columns-layout">
                <div>
                    <section>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h2 className="section-head-title" style={{ fontSize: '1.1rem' }}>🐾 Qual pet vai à consulta?</h2>
                            <span style={{ fontSize: '0.78rem', color: 'var(--color-neutral-muted)' }}>{pets.length} pets cadastrados</span>
                        </div>

                        <div className="pet-pick-cards-grid">
                            {pets.map(p => {
                                const isSelected = (petSelecionado.petId || petSelecionado.id) === (p.petId || p.id);
                                return (
                                    <div
                                        key={p.petId || p.id}
                                        className={`pet-pick-card ${isSelected ? 'selected' : ''}`}
                                        onClick={() => setPetSelecionado(p)}
                                    >
                                        {isSelected && <span className="pet-pick-check-badge">✓</span>}
                                        <div className="pet-pick-avatar">{p.especie === 'Gato' ? '🐈' : '🐕'}</div>
                                        <strong style={{ fontSize: '0.9rem' }}>{p.petNome || p.nome}</strong>
                                        <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>{p.especie || 'Cachorro'} • {p.idade || 4} anos</span>
                                    </div>
                                );
                            })}

                            <div className="pet-pick-card" style={{ borderStyle: 'dashed' }} onClick={onCancelar}>
                                <div className="pet-pick-avatar" style={{ fontSize: '1.2rem', fontWeight: 800 }}>+</div>
                                <strong style={{ fontSize: '0.85rem' }}>Outro pet</strong>
                                <span style={{ fontSize: '0.7rem', color: 'var(--color-neutral-muted)' }}>Cadastrar novo</span>
                            </div>
                        </div>
                    </section>

                    <section style={{ marginTop: '28px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h2 className="section-head-title" style={{ fontSize: '1.1rem' }}>🩺 Escolha o veterinário</h2>
                            <span style={{ fontSize: '0.78rem', color: 'var(--color-neutral-muted)' }}>Unidade Jardins • São Paulo</span>
                        </div>

                        <div className="vet-selection-list">
                            <div
                                className={`vet-card-option ${vetSelecionado.id === 1 ? 'selected' : ''}`}
                                onClick={() => setVetSelecionado({ id: 1, nome: 'Dra. Ana Souza' })}
                            >
                                <div className="vet-card-left">
                                    <div className="vet-photo-circle">👩‍⚕️</div>
                                    <div>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <strong style={{ fontSize: '1rem' }}>Dra. Ana Souza</strong>
                                            <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>CRMV-SP 42.189</span>
                                        </div>
                                        <div style={{ fontSize: '0.8rem', color: 'var(--color-primary)', fontWeight: 700 }}>
                                            Clínica geral & Dermatologia
                                        </div>
                                        <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>
                      ⭐ 4.9 (124 avaliações) • Sala 03 — Térreo
                    </span>
                                    </div>
                                </div>
                                <Badge variant="success">● Disponível hoje</Badge>
                            </div>

                            <div
                                className={`vet-card-option ${vetSelecionado.id === 2 ? 'selected' : ''}`}
                                onClick={() => setVetSelecionado({ id: 2, nome: 'Dr. Vinícius Lima' })}
                            >
                                <div className="vet-card-left">
                                    <div className="vet-photo-circle">👨‍⚕️</div>
                                    <div>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <strong style={{ fontSize: '1rem' }}>Dr. Vinícius Lima</strong>
                                            <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>CRMV-SP 38.450</span>
                                        </div>
                                        <div style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>
                                            Dermatologia & Alergologia
                                        </div>
                                        <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>
                      ⭐ 4.8 (98 avaliações) • Sala 07 — Piso 1
                    </span>
                                    </div>
                                </div>
                                <Button variant="outlined" size="sm">Selecionar</Button>
                            </div>
                        </div>
                    </section>

                    <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '16px 20px', borderRadius: 'var(--radius-lg)', marginTop: '24px', display: 'flex', gap: '12px', alignItems: 'center' }}>
                        <span style={{ fontSize: '1.4rem' }}>🏥</span>
                        <div>
                            <strong style={{ fontSize: '0.85rem' }}>Tipo de atendimento: Consulta Presencial</strong>
                            <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>
                                A consulta inclui triagem de sinais vitais, avaliação física completa e emissão de receita digital integrada.
                            </div>
                        </div>
                    </div>
                </div>

                <div>
                    <section className="mini-calendar-box">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                            <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 800 }}>📅 Data e horário</h3>
                            <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>Horário de Brasília</span>
                        </div>

                        <div className="calendar-month-nav">
                            <button type="button" className="cal-nav-arrow" onClick={() => mudarMes(-1)}>‹</button>
                            <span>{dataAtual.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' }).toUpperCase()}</span>
                            <button type="button" className="cal-nav-arrow" onClick={() => mudarMes(1)}>›</button>
                        </div>

                        <div className="calendar-weekdays-row">
                            <span>D</span><span>S</span><span>T</span><span>Q</span><span>Q</span><span>S</span><span>S</span>
                        </div>

                        <div className="calendar-days-grid">
                            {Array.from({ length: primeiroDiaSemana }).map((_, i) => (
                                <span key={`empty-${i}`} className="cal-day-cell empty" />
                            ))}
                            {diasMes.map(d => (
                                <span
                                    key={d}
                                    className={`cal-day-cell ${diaSelecionado === d ? 'selected' : ''}`}
                                    onClick={() => setDiaSelecionado(d)}
                                >
                  {d}
                </span>
                            ))}
                        </div>

                        <div style={{ margin: '20px 0 10px 0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>Horários para {diaSelecionado}</span>
                            <span style={{ fontSize: '0.72rem', color: 'var(--color-tertiary)', fontWeight: 700 }}>6 vagas abertas</span>
                        </div>

                        <div className="slots-grid">
                            {horariosDisponiveis.map(slot => (
                                <button
                                    key={slot.hora}
                                    type="button"
                                    disabled={slot.ocupado}
                                    onClick={() => setHorarioEscolhido(slot.hora)}
                                    className={`slot-btn ${horarioEscolhido === slot.hora ? 'selected' : ''}`}
                                >
                                    {slot.hora}
                                </button>
                            ))}
                        </div>

                        <div className="booking-summary-side-card">
                            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                                <span>📅</span>
                                <div style={{ fontSize: '0.85rem' }}>
                                    <strong>{petSelecionado.petNome || petSelecionado.nome} • {vetSelecionado.nome}</strong>
                                    <div style={{ color: 'var(--color-neutral-muted)', fontSize: '0.78rem' }}>
                                        {diaSelecionado}/{mes + 1}/{ano} às {horarioEscolhido} • Unidade Jardins
                                    </div>
                                </div>
                            </div>
                        </div>

                        <Button
                            variant="primary"
                            size="lg"
                            style={{ width: '100%', marginTop: '16px' }}
                            disabled={loading}
                            onClick={handleConfirmar}
                        >
                            {loading ? 'Confirmando...' : 'Confirmar agendamento →'}
                        </Button>
                    </section>
                </div>
            </div>
        </div>
    );
}