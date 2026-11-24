import React from 'react';

export default function SelecaoTutorPet({ pets, tutorId, petId, onTutorChange, onPetChange,
    prefixo, carregando = false, disabled = false }) {
    const tutores = [...new Map(pets.filter(p => p.tutorId != null)
        .map(p => [String(p.tutorId), { id: String(p.tutorId), nome: p.tutorNome }])).values()]
        .sort((a, b) => (a.nome || '').localeCompare(b.nome || '', 'pt-BR') || a.id.localeCompare(b.id, undefined, { numeric: true }));
    const relacionados = pets.filter(p => tutorId && String(p.tutorId) === String(tutorId));
    return <div className="form-grid-2 selecao-tutor-pet">
        <div className="input-group">
            <label className="input-label" htmlFor={prefixo + '-tutor'}>TUTOR</label>
            <select id={prefixo + '-tutor'} className="select-custom" value={tutorId}
                disabled={disabled || carregando || !tutores.length} onChange={e => onTutorChange(e.target.value)} required>
                <option value="">{carregando ? 'Carregando tutores...' : 'Selecione um tutor...'}</option>
                {tutores.map(t => <option key={t.id} value={t.id}>{t.nome || 'Tutor sem nome'} · cadastro #{t.id}</option>)}
            </select>
        </div>
        <div className="input-group">
            <label className="input-label" htmlFor={prefixo + '-pet'}>PACIENTE</label>
            <select id={prefixo + '-pet'} className="select-custom" value={petId}
                disabled={disabled || carregando || !tutorId || !relacionados.length} onChange={e => onPetChange(e.target.value)} required>
                <option value="">{tutorId ? 'Selecione um pet...' : 'Selecione primeiro um tutor'}</option>
                {relacionados.map(p => <option key={p.id} value={p.id}>{p.nome} ({p.especie || 'Espécie não informada'}) · pet #{p.id}</option>)}
            </select>
        </div>
    </div>;
}
