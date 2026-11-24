import React, { useEffect, useState } from 'react';
import { obterMinhaAgenda } from '../api/api';
import { Badge, Button } from './common/UIComponents';

const dataLocal = () => {
    const agora = new Date();
    return `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-${String(agora.getDate()).padStart(2, '0')}`;
};
const hora = (dataHora) => dataHora.slice(11, 16);
const statusConsulta = {
    AGENDADA: 'Agendada', CONFIRMADA: 'Confirmada', REALIZADA: 'Realizada',
    CANCELADA: 'Cancelada', NAO_COMPARECEU: 'Não compareceu'
};

export default function MinhaAgenda() {
    const [data, setData] = useState(dataLocal);
    const [agenda, setAgenda] = useState(null);
    const [erro, setErro] = useState('');
    const [carregando, setCarregando] = useState(true);
    const [atualizacao, setAtualizacao] = useState(0);

    useEffect(() => {
        let ativo = true;
        setCarregando(true);
        setAgenda(null);
        setErro('');
        if (!data) {
            setCarregando(false);
            return () => { ativo = false; };
        }
        obterMinhaAgenda(data).then(({ data: resultado }) => {
            if (ativo) setAgenda(resultado);
        }).catch(() => {
            if (ativo) setErro('Não foi possível carregar sua agenda. Tente novamente.');
        }).finally(() => {
            if (ativo) setCarregando(false);
        });
        return () => { ativo = false; };
    }, [data, atualizacao]);

    return (
        <section className="form-container minha-agenda">
            <div className="form-card">
                <h1 className="form-title">Minha agenda</h1>
                <div className="agenda-controles">
                    <div className="input-group">
                        <label htmlFor="agenda-data" className="input-label">Data da agenda</label>
                        <input id="agenda-data" className="select-custom" type="date" value={data}
                            onChange={(e) => setData(e.target.value)} />
                    </div>
                    <Button variant="outlined" disabled={carregando || !data}
                        onClick={() => setAtualizacao((valor) => valor + 1)}>Atualizar agenda</Button>
                </div>
                {!data && <p>Selecione uma data para consultar sua agenda.</p>}
                {carregando && <p role="status">Carregando agenda…</p>}
                {erro && <p className="alert-box error" role="alert">{erro}</p>}
                {agenda && (
                    <>
                        <h2 className="form-section-title">Horários de atendimento</h2>
                        {!agenda.horarios.length && <p>Nenhum horário de atendimento nesta data.</p>}
                        <ul className="agenda-horarios">
                            {agenda.horarios.map((slot) => {
                                const reserva = agenda.consultas.find((consulta) =>
                                    consulta.dataHora === slot.horario &&
                                    !['CANCELADA', 'NAO_COMPARECEU'].includes(consulta.status));
                                const passado = new Date(slot.horario) < new Date();
                                const situacao = reserva ? 'Reservado' : slot.disponivel
                                    ? passado ? 'Sem reserva' : 'Livre' : 'Indisponível';
                                return (
                                    <li key={slot.horario} className="agenda-horario">
                                        <strong>{hora(slot.horario)}</strong>
                                        <Badge variant={reserva ? 'warning' : slot.disponivel ? 'success' : 'neutral'}>
                                            {situacao}
                                        </Badge>
                                        {reserva && <span>{reserva.petNome} · {reserva.tutorNome}</span>}
                                    </li>
                                );
                            })}
                        </ul>
                        <h2 className="form-section-title">Consultas do dia</h2>
                        {!agenda.consultas.length ? <p>Nenhuma consulta nesta data.</p> : (
                            <div className="agenda-tabela">
                                <table className="panel-table">
                                    <thead><tr><th>Horário</th><th>Pet</th><th>Tutor</th><th>Status</th></tr></thead>
                                    <tbody>
                                        {agenda.consultas.map((consulta) => (
                                            <tr key={consulta.id}>
                                                <td>{hora(consulta.dataHora)}</td>
                                                <td>{consulta.petNome}</td>
                                                <td>{consulta.tutorNome}</td>
                                                <td>{statusConsulta[consulta.status] || consulta.status}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </>
                )}
            </div>
        </section>
    );
}
