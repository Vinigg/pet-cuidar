import React from 'react';
import { Button } from '../common/UIComponents';
import { CartaoPet, dataHora, StatusConsulta } from './TutorPresentation';

export default function TutorHome({ conta, pets, consultas, navegar }) {
    const proxima = consultas.filter(c => ['AGENDADA', 'CONFIRMADA'].includes(c.status) && new Date(c.dataHora) >= new Date())
        .sort((a, b) => new Date(a.dataHora) - new Date(b.dataHora))[0];
    const ultimas = consultas.filter(c => new Date(c.dataHora) < new Date())
        .sort((a, b) => new Date(b.dataHora) - new Date(a.dataHora)).slice(0, 3);
    return <div className="tutor-page-wrapper">
        <header className="tutor-dash-head">
            <div><span className="tutor-pill-tag">● Área do Tutor</span><h1 className="tutor-main-title">Olá, {conta.nome}!</h1><p className="tutor-main-sub">Veja os cuidados e os próximos compromissos de seus companheiros.</p></div>
            <div className="active-pets-pill"><span aria-hidden="true">🐕🐈</span><div><span>COMPANHEIROS</span><strong>{pets.length} {pets.length === 1 ? 'pet cadastrado' : 'pets cadastrados'}</strong></div></div>
        </header>
        <div className="tutor-home-grid">
            <div>
                <section className="next-consult-banner-wrap">
                    <div className="next-consult-header"><h2 className="section-head-title">📅 Próxima consulta</h2>{proxima && <StatusConsulta status={proxima.status} />}</div>
                    {proxima ? <><div className="next-consult-pet-row"><div className="pet-avatar-large" aria-hidden="true">🐾</div><div><h3>{proxima.petNome}</h3><p>{proxima.veterinarioNome}</p></div></div>
                        <div className="next-consult-details-strip"><div className="strip-item-col"><span className="strip-item-label">DATA & HORÁRIO</span><strong>{dataHora(proxima.dataHora)}</strong></div></div>
                        <div className="next-consult-footer-row"><Button variant="outlined" size="sm" onClick={() => navegar('detalhes-consulta', proxima.id)}>Ver detalhes</Button></div></>
                        : <p>Nenhuma consulta futura.</p>}
                </section>
                <section className="restored-home-pets">
                    <h2 className="section-head-title">Meus pets</h2>
                    <div className="pets-gallery-grid">{pets.slice(0, 2).map(pet => <CartaoPet key={pet.id} pet={pet} onAgendar={() => navegar('agendar-consulta')} />)}
                        <button className="add-pet-dash-card" type="button" onClick={() => navegar('cadastrar-pet')}><span className="add-pet-plus-circle">+</span><strong>+ Adicionar pet</strong><span>Cadastre mais um companheiro</span></button>
                    </div>
                    <Button variant="outlined" onClick={() => navegar('meus-pets')}>Gerenciar meus pets</Button>
                </section>
            </div>
            <aside className="restored-sidebar">
                <div className="cta-agendamento-dark-card"><span className="cta-tag-pill">🛡️ Agendamento Fácil</span><h2 className="cta-dark-title">Precisa de atendimento?</h2><p className="cta-dark-desc">Escolha seu pet, profissional e um horário disponível na clínica.</p><Button variant="inverted" onClick={() => navegar('agendar-consulta')}>Agendar consulta</Button></div>
                <div className="tutor-consultas-table-card restored-consult-card"><h2 className="section-head-title">🕐 Últimas consultas</h2>
                    {!ultimas.length && <p>Nenhuma consulta anterior.</p>}
                    {ultimas.map(c => <article className="restored-recent-consult" key={c.id}><strong>{c.petNome}</strong><StatusConsulta status={c.status} /><p>{c.veterinarioNome} • {dataHora(c.dataHora)}</p><Button size="sm" variant="outlined" onClick={() => navegar('detalhes-consulta', c.id)}>Ver detalhes</Button></article>)}
                    <Button variant="outlined" onClick={() => navegar('consultas')}>Ver todas as consultas</Button>
                </div>
            </aside>
        </div>
    </div>;
}
