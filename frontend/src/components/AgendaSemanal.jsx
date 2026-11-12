import React, { useState } from 'react';
import { Button, Badge } from './common/UIComponents';

export default function AgendaSemanal({ onNovaConsulta }) {
    const [offsetSemana, setOffsetSemana] = useState(0);

    const obterDiasSemana = (offset) => {
        // Referência base: 22 de Outubro de 2026 (Quinta-feira)
        const base = new Date(2026, 9, 22);
        base.setDate(base.getDate() + offset * 7);

        // Ajusta para segunda-feira da semana
        const diaSemana = base.getDay();
        const diffParaSegunda = base.getDate() - diaSemana + (diaSemana === 0 ? -6 : 1);
        const segunda = new Date(base.setDate(diffParaSegunda));

        const nomesDias = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];
        return nomesDias.map((nome, i) => {
            const d = new Date(segunda);
            d.setDate(segunda.getDate() + i);
            const diaNum = String(d.getDate()).padStart(2, '0');
            const mesNum = String(d.getMonth() + 1).padStart(2, '0');
            const eHoje = offset === 0 && i === 3; // Quinta 22/10
            return {
                dia: nome,
                data: `${diaNum}/${mesNum}`,
                ano: d.getFullYear(),
                hoje: eHoje
            };
        });
    };

    const dias = obterDiasSemana(offsetSemana);
    const horarios = ['08:00', '09:00', '10:00', '11:00', '14:00', '15:00', '16:00', '17:00'];
    const periodoTexto = `${dias[0].data} a ${dias[6].data} de ${dias[3].ano}`;

    return (
        <div className="clinica-container">
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <div>
          <span style={{ fontSize: '0.75rem', fontWeight: 800, color: 'var(--color-primary)' }}>
            RECEPÇÃO • AGENDA SEMANAL
          </span>
                    <h1 className="tutor-main-title" style={{ fontSize: '2rem', margin: '4px 0' }}>Agenda da Clínica</h1>
                    <p className="tutor-main-sub">Visão semanal unificada da escala dos veterinários e horários de consultório.</p>
                </div>
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', background: '#FFFFFF', padding: '6px 12px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-neutral-border)' }}>
                        <button
                            type="button"
                            onClick={() => setOffsetSemana(offsetSemana - 1)}
                            style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontWeight: 900, fontSize: '1rem', color: 'var(--color-primary)' }}
                            title="Semana anterior"
                        >
                            ◀
                        </button>
                        <span style={{ fontWeight: 800, fontSize: '0.88rem' }}>📅 {periodoTexto}</span>
                        <button
                            type="button"
                            onClick={() => setOffsetSemana(offsetSemana + 1)}
                            style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontWeight: 900, fontSize: '1rem', color: 'var(--color-primary)' }}
                            title="Próxima semana"
                        >
                            ▶
                        </button>
                    </div>
                    {offsetSemana !== 0 && (
                        <Button variant="outlined" size="sm" onClick={() => setOffsetSemana(0)}>
                            Hoje
                        </Button>
                    )}
                    <Button variant="primary" size="sm" onClick={() => onNovaConsulta ? onNovaConsulta() : alert('Abrindo nova consulta')}>
                        + Nova consulta
                    </Button>
                </div>
            </header>

            <div style={{ width: '100%', overflowX: 'auto', paddingBottom: '12px' }}>
                <div className="weekly-agenda-grid" style={{ minWidth: '780px' }}>
                    <div className="weekly-cell-header">Horário</div>
                    {dias.map((d) => (
                        <div key={d.data} className={`weekly-cell-header ${d.hoje ? 'today' : ''}`}>
                            {d.dia} {d.data} {d.hoje && '• HOJE'}
                        </div>
                    ))}

                    {horarios.map((h) => (
                        <React.Fragment key={h}>
                            <div className="weekly-hour-cell" style={{ fontWeight: 800, color: 'var(--color-neutral-muted)' }}>{h}</div>
                            {dias.map((d, idx) => (
                                <div key={idx} className="weekly-hour-cell">
                                    {d.hoje && h === '08:00' && (
                                        <div className="weekly-event-pill">
                                            <strong>Thor (Golden)</strong>
                                            <span>Dra. Ana • Realizada</span>
                                        </div>
                                    )}
                                    {d.hoje && h === '10:00' && (
                                        <div className="weekly-event-pill">
                                            <strong>Mel (SRD)</strong>
                                            <span>Dra. Ana • Realizada</span>
                                        </div>
                                    )}
                                    {d.hoje && h === '15:00' && (
                                        <div className="weekly-event-pill danger">
                                            <strong>Billy (Beagle)</strong>
                                            <span>Não compareceu</span>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </React.Fragment>
                    ))}
                </div>
            </div>
        </div>
    );
}