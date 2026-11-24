import React, { useState } from 'react';
import { Button, Input } from '../common/UIComponents';
import { CartaoPet } from './TutorPresentation';

export default function TutorMeusPets({ pets, onCadastrarOutroPet, onAgendarConsulta }) {
    const [busca, setBusca] = useState('');
    const [especie, setEspecie] = useState('');
    const especies = [...new Set(pets.map(p => p.especie).filter(Boolean))].sort();
    const filtrados = pets.filter(p => p.nome.toLowerCase().includes(busca.toLowerCase()) && (!especie || p.especie === especie));
    return <div className="meus-pets-container">
        <header className="meus-pets-header"><div><span className="clinic-eyebrow">SEUS COMPANHEIROS</span><h1 className="form-title">Meus pets</h1><p className="form-subtitle">Acompanhe os dados e cuidados cadastrados dos seus pets.</p></div><Button onClick={onCadastrarOutroPet}>Cadastrar pet</Button></header>
        <div className="meus-pets-toolbar">
            <Input aria-label="Buscar pet" placeholder="Buscar pelo nome do pet..." value={busca} onChange={e => setBusca(e.target.value)} />
            <div className="species-filter-chips"><button className={`species-filter-btn ${!especie ? 'active' : ''}`} type="button" onClick={() => setEspecie('')}>Todos ({pets.length})</button>
                {especies.map(e => <button className={`species-filter-btn ${especie === e ? 'active' : ''}`} key={e} type="button" onClick={() => setEspecie(e)}>{e}</button>)}
            </div>
        </div>
        {!filtrados.length && <p className="empty-state">{pets.length ? 'Nenhum pet corresponde à busca e espécie.' : 'Nenhum pet cadastrado.'}</p>}
        <div className="pets-cards-grid">{filtrados.map(p => <CartaoPet key={p.id} pet={p} onAgendar={onAgendarConsulta} />)}</div>
    </div>;
}
