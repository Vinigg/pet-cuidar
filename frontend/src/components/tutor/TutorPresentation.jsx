import React from 'react';
import { Badge, Button } from '../common/UIComponents';

export const statusInfo = {
    AGENDADA: ['Agendada', 'warning'], CONFIRMADA: ['Confirmada', 'success'],
    REALIZADA: ['Realizada', 'primary'], CANCELADA: ['Cancelada', 'danger'],
    NAO_COMPARECEU: ['Não compareceu', 'danger'],
};
export const dataHora = value => new Date(value).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
export const podeAlterar = consulta => ['AGENDADA', 'CONFIRMADA'].includes(consulta.status) && new Date(consulta.dataHora) > new Date();
export const emojiPet = especie => especie?.toLowerCase() === 'gato' ? '🐈' : '🐕';

export function StatusConsulta({ status }) {
    const [label, variant] = statusInfo[status] || [status, 'neutral'];
    return <Badge variant={variant}>{label}</Badge>;
}

export function CartaoPet({ pet, onAgendar }) {
    return <article className="pet-detailed-card">
        <div className="pet-card-image-box">
            <span className="pet-card-emoji-placeholder" aria-hidden="true">{emojiPet(pet.especie)}</span>
            {pet.alergiasCriticas && <span className="pet-card-tag-alert">⚠️ Alerta clínico cadastrado</span>}
        </div>
        <div className="pet-card-content">
            <h2 className="section-head-title">{pet.nome}</h2>
            <p>{pet.especie || 'Espécie não informada'}{pet.idade != null && ` • ${pet.idade} ${pet.idade === 1 ? 'ano' : 'anos'}`}</p>
            {pet.alergiasCriticas && <p className="pet-allergy-note"><strong>Cuidados especiais:</strong> {pet.alergiasCriticas}</p>}
            {onAgendar && <Button variant="outlined" size="sm" onClick={() => onAgendar(pet.id)}>Agendar consulta</Button>}
        </div>
    </article>;
}

export function ResumoConsulta({ consulta, pet }) {
    return <div className="tutor-consultas-table-card restored-consult-card">
        <div className="restored-patient-heading">
            <div className="pet-thumb-circle" aria-hidden="true">{emojiPet(pet?.especie)}</div>
            <div><h2>{consulta.petNome}</h2>{pet?.especie && <span>{pet.especie}</span>}</div>
            <StatusConsulta status={consulta.status} />
        </div>
        {pet?.alergiasCriticas && <p className="alert-allergy-box">⚠️ {pet.alergiasCriticas}</p>}
        <dl className="restored-detail-grid">
            <div><dt>DATA E HORÁRIO</dt><dd>{dataHora(consulta.dataHora)}</dd></div>
            <div><dt>PROFISSIONAL</dt><dd>{consulta.veterinarioNome || 'Não informado'}</dd></div>
        </dl>
    </div>;
}

export function ConfirmarCancelamento({ consulta, onFechar, onConfirmar, cancelando, erro }) {
    return <div className="modal-overlay">
        <div className="modal-cancel-card" role="dialog" aria-modal="true" aria-labelledby="cancelar-titulo">
            <div className="cancel-alert-icon-circle" aria-hidden="true">⚠️</div>
            <h2 id="cancelar-titulo" className="cancel-modal-title">Cancelar consulta?</h2>
            <p className="cancel-modal-desc">Consulta de <strong>{consulta.petNome}</strong> em {dataHora(consulta.dataHora)}. O horário será liberado após a confirmação do servidor.</p>
            {erro && <p className="alert-box error" role="alert">{erro}</p>}
            <div className="cancel-modal-actions">
                <Button variant="outlined" disabled={cancelando} onClick={onFechar}>Manter consulta</Button>
                <Button disabled={cancelando} onClick={onConfirmar}>{cancelando ? 'Cancelando...' : 'Confirmar cancelamento'}</Button>
            </div>
        </div>
    </div>;
}
