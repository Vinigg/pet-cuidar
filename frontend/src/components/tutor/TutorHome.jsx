import React from 'react';
import { Button, Badge } from '../common/UIComponents';

export default function TutorHome({ conta, pets, consultas, navegar }) {
    const proxima = consultas
        .filter((consulta) => ['AGENDADA', 'CONFIRMADA'].includes(consulta.status) && new Date(consulta.dataHora) >= new Date())
        .sort((a, b) => new Date(a.dataHora) - new Date(b.dataHora))[0];

    return (
        <div className="tutor-page-wrapper">
            <header className="tutor-dash-head">
                <div>
                    <h1 className="tutor-main-title">Olá, {conta.nome}!</h1>
                    <p className="tutor-main-sub">Acompanhe seus pets e consultas.</p>
                </div>
                <Button onClick={() => navegar('agendar-consulta')}>Agendar consulta</Button>
            </header>
            <section className="next-consult-banner-wrap">
                <h2 className="section-head-title">Próxima consulta</h2>
                {proxima ? (
                    <>
                        <p><strong>{proxima.petNome}</strong> · {proxima.veterinarioNome}</p>
                        <p>{new Date(proxima.dataHora).toLocaleString('pt-BR')} · <Badge>{proxima.status}</Badge></p>
                        <Button variant="outlined" onClick={() => navegar('detalhes-consulta', proxima.id)}>
                            Ver detalhes
                        </Button>
                    </>
                ) : <p>Nenhuma consulta futura.</p>}
            </section>
            <section className="tutor-consultas-table-card" style={{ padding: 24 }}>
                <h2 className="section-head-title">Meus pets</h2>
                {pets.length ? <ul>{pets.map((pet) => <li key={pet.id}>{pet.nome}</li>)}</ul>
                    : <p>Nenhum pet cadastrado.</p>}
                <Button variant="outlined" onClick={() => navegar('meus-pets')}>Gerenciar meus pets</Button>
            </section>
        </div>
    );
}
