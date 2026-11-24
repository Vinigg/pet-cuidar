import React, { useEffect, useState } from 'react';
import { dataLocal } from '../utils/datas';

export default function CalendarioConsulta({ data, onSelecionar, disabled }) {
    const [mes, setMes] = useState(() => (data || dataLocal()).slice(0, 7));
    useEffect(() => { if (data) setMes(data.slice(0, 7)); }, [data]);
    const [ano, numeroMes] = mes.split('-').map(Number);
    const inicio = new Date(ano, numeroMes - 1, 1);
    const quantidade = new Date(ano, numeroMes, 0).getDate();
    const navegar = delta => setMes(dataLocal(new Date(ano, numeroMes - 1 + delta, 1)).slice(0, 7));
    return <div className="mini-calendar-box">
        <div className="calendar-month-nav"><button type="button" className="cal-nav-arrow" disabled={disabled} aria-label="Mês anterior" onClick={() => navegar(-1)}>‹</button><span>{inicio.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })}</span><button type="button" className="cal-nav-arrow" disabled={disabled} aria-label="Próximo mês" onClick={() => navegar(1)}>›</button></div>
        <div className="calendar-weekdays-row">{['D', 'S', 'T', 'Q', 'Q', 'S', 'S'].map((d, i) => <span key={i}>{d}</span>)}</div>
        <div className="calendar-days-grid">
            {Array.from({ length: inicio.getDay() }, (_, i) => <span className="cal-day-cell empty" key={'vazio' + i} />)}
            {Array.from({ length: quantidade }, (_, i) => {
                const iso = mes + '-' + String(i + 1).padStart(2, '0');
                return <button type="button" key={iso} className={`cal-day-cell ${data === iso ? 'selected' : ''}`}
                    disabled={disabled || iso < dataLocal()} aria-label={iso.split('-').reverse().join('/')}
                    aria-pressed={data === iso} onClick={() => onSelecionar(iso)}>{i + 1}</button>;
            })}
        </div>
    </div>;
}
