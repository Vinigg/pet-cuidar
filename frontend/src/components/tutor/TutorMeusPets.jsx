import React from 'react';
import { Badge, Button } from '../common/UIComponents';

export default function TutorMeusPets({ pets, onCadastrarOutroPet, onAgendarConsulta }) {
    return (
        <div className="tutor-page-wrapper">
            <header className="tutor-dash-head">
                <h1 className="tutor-main-title">Meus pets</h1>
                <Button onClick={onCadastrarOutroPet}>Cadastrar pet</Button>
            </header>
            {!pets.length && <p className="empty-state">Nenhum pet cadastrado.</p>}
            <div className="pets-gallery-grid">
                {pets.map((pet) => (
                    <article className="pet-profile-card" key={pet.id}>
                        <div className="pet-card-content">
                            <h2 className="section-head-title">{pet.nome}</h2>
                            <p>{pet.especie || 'Espécie não informada'}</p>
                            {pet.idade !== null && <Badge>{pet.idade} {pet.idade === 1 ? 'ano' : 'anos'}</Badge>}
                            {pet.alergiasCriticas && <p><strong>Alergias</strong> {pet.alergiasCriticas}</p>}
                            <Button variant="outlined" onClick={onAgendarConsulta}>Agendar consulta</Button>
                        </div>
                    </article>
                ))}
            </div>
        </div>
    );
}
