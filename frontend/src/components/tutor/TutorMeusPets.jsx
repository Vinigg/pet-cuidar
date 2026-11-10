import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Button, Badge, Input } from '../common/UIComponents';

const PETS_MOCK_LISTA = [
    { id: 1, petId: 1, petNome: 'Thor', especie: 'Cachorro', raca: 'Golden Retriever', idade: 4, peso: '32,4 kg', microchip: '981.098.223.109', veterinario: 'Dra. Ana Souza', alerta: 'Alergia crítica', sexo: 'Macho • Castrado', ultimaConsulta: '15/09/2026' },
    { id: 2, petId: 2, petNome: 'Luna', especie: 'Gato', raca: 'Siamês Tradicional', idade: 2, peso: '4,1 kg', microchip: '981.042.871.405', veterinario: 'Dr. Vinícius Lima', alerta: 'Saúde em dia', sexo: 'Fêmea • Castrada', ultimaConsulta: '10/08/2026' },
    { id: 3, petId: 3, petNome: 'Mel', especie: 'Cachorro', raca: 'Cachorro SRD', idade: 7, peso: '14,8 kg', microchip: '981.112.509.332', veterinario: 'Dra. Ana Souza', alerta: 'Vacina V10 pendente', sexo: 'Fêmea • Castrada', ultimaConsulta: '24/05/2026' }
];

const CARTEIRA_IMUNIZACOES = [
    { id: 1, pet: 'Mel', tipo: 'Vacina', nome: 'Vacina V10 (Décupla Canina)', status: 'PENDENTE', dose: 'Reforço Anual', previsao: 'Novembro de 2026', lote: 'Aguardando agendamento' },
    { id: 2, pet: 'Thor', tipo: 'Vacina', nome: 'Vacina V10 Canina', status: 'EM DIA', dose: 'Reforço Anual', previsao: '15/10/2026', lote: 'BR-98442' },
    { id: 3, pet: 'Thor', tipo: 'Vacina', nome: 'Vacina Antirrábica', status: 'EM DIA', dose: 'Anual', previsao: '02/08/2027', lote: 'AR-33910' },
    { id: 4, pet: 'Luna', tipo: 'Vacina', nome: 'Vacina V4 Felina', status: 'EM DIA', dose: 'Reforço', previsao: '10/08/2027', lote: 'FEL-1002' },
    { id: 5, pet: 'Luna', tipo: 'Vermífugo', nome: 'Vermífugo Amplo Espectro', status: 'EM DIA', dose: 'Dose Semestral', previsao: 'Dezembro de 2026', lote: 'VERM-449' }
];

export default function TutorMeusPets({ onCadastrarOutroPet, onAgendarConsulta }) {
    const [pets, setPets] = useState(PETS_MOCK_LISTA);
    const [filtroEspecie, setFiltroEspecie] = useState('todos');
    const [buscaTexto, setBuscaTexto] = useState('');
    const [modalCarteiraAberto, setModalCarteiraAberto] = useState(false);
    const [petFiltroCarteira, setPetFiltroCarteira] = useState('todos');

    useEffect(() => {
        async function carregar() {
            try {
                const res = await axios.get('http://localhost:8080/pets/opcoes');
                if (res.data && res.data.length > 0) {
                    setPets(res.data);
                }
            } catch {
                setPets(PETS_MOCK_LISTA);
            }
        }
        carregar();
    }, []);

    const petsFiltrados = pets.filter((p) => {
        const nomeBate = (p.petNome || p.nome || '').toLowerCase().includes(buscaTexto.toLowerCase());
        const especieBate = filtroEspecie === 'todos' || (p.especie || '').toLowerCase() === filtroEspecie.toLowerCase();
        return nomeBate && especieBate;
    });

    const imunizacoesExibidas = CARTEIRA_IMUNIZACOES.filter(
        (item) => petFiltroCarteira === 'todos' || item.pet.toLowerCase() === petFiltroCarteira.toLowerCase()
    );

    return (
        <div className="meus-pets-container">
            {/* Banner de Lembrete Preventivo Superior com Texto Idêntico ao Figma */}
            <div className="preventive-banner">
                <div className="preventive-banner-content">
                    <div className="preventive-banner-icon">📅</div>
                    <div>
            <span className="preventive-banner-date">
              QUINTA-FEIRA, 22 DE OUTUBRO DE 2026 • Lembrete Preventivo
            </span>
                        <div className="preventive-banner-text">
                            Mel possui atualização da vacina V10 agendável para o próximo mês. Mantenha as proteções em dia!
                        </div>
                    </div>
                </div>
                <button
                    type="button"
                    className="btn-ver-carteira"
                    onClick={() => setModalCarteiraAberto(true)}
                >
                    ✓ Ver Carteira
                </button>
            </div>

            <div className="meus-pets-header">
                <div>
          <span style={{ fontSize: '0.75rem', fontWeight: 800, color: 'var(--color-primary)' }}>
            PAINEL DE ACOMPANHAMENTO
          </span>
                    <h1 className="form-title" style={{ marginTop: '4px' }}>Meus pets</h1>
                    <p className="form-subtitle">Gerencie os dados dos seus pets e acompanhe a saúde preventiva com facilidade.</p>
                </div>
                <Button variant="primary" size="md" onClick={onCadastrarOutroPet}>
                    + Adicionar pet
                </Button>
            </div>

            <div className="meus-pets-toolbar">
                <div className="meus-pets-search-wrap">
                    <Input
                        placeholder="Buscar pet por nome, espécie ou microchip..."
                        value={buscaTexto}
                        onChange={(e) => setBuscaTexto(e.target.value)}
                    />
                </div>
                <div className="species-filter-chips">
                    <button
                        type="button"
                        className={`species-filter-btn ${filtroEspecie === 'todos' ? 'active' : ''}`}
                        onClick={() => setFiltroEspecie('todos')}
                    >
                        📋 Todos ({pets.length})
                    </button>
                    <button
                        type="button"
                        className={`species-filter-btn ${filtroEspecie === 'cachorro' ? 'active' : ''}`}
                        onClick={() => setFiltroEspecie('cachorro')}
                    >
                        🐶 Cachorros
                    </button>
                    <button
                        type="button"
                        className={`species-filter-btn ${filtroEspecie === 'gato' ? 'active' : ''}`}
                        onClick={() => setFiltroEspecie('gato')}
                    >
                        🐱 Gatos
                    </button>
                </div>
            </div>

            <div className="pets-cards-grid">
                {petsFiltrados.map((p, idx) => (
                    <div key={p.petId || p.id || idx} className="pet-detailed-card">
                        <div className="pet-card-image-box">
                            <span className="pet-card-emoji-placeholder">{p.especie === 'Gato' ? '🐈' : '🐕'}</span>
                            <span className={p.alerta === 'Alergia crítica' ? 'pet-card-tag-alert' : 'pet-card-tag-healthy'}>
                {p.alerta || 'Saúde em dia'}
              </span>
                            <span className="pet-card-gender-chip">{p.sexo || 'Castrado(a)'}</span>
                        </div>
                        <div className="pet-card-body">
                            <div className="pet-card-body-top">
                                <h3 className="pet-card-name">{p.petNome || p.nome}</h3>
                                <Badge variant="success">Ativo</Badge>
                            </div>
                            <p className="pet-card-breed-desc">{p.raca || p.especie || 'Pet'} • {p.idade || 4} anos</p>
                            <div className="pet-card-specs-list">
                                <div className="pet-card-spec-row">
                                    <span className="pet-card-spec-label">📅 Última consulta:</span>
                                    <span>{p.ultimaConsulta || '15/09/2026'}</span>
                                </div>
                                <div className="pet-card-spec-row">
                                    <span className="pet-card-spec-label">🩺 Veterinária:</span>
                                    <span>{p.veterinario || 'Dra. Ana Souza'}</span>
                                </div>
                            </div>
                            <div className="pet-card-actions">
                                <Button variant="outlined" size="sm" onClick={() => { setPetFiltroCarteira((p.petNome || p.nome).toLowerCase()); setModalCarteiraAberto(true); }}>
                                    Carteira
                                </Button>
                                <Button variant="primary" size="sm" onClick={onAgendarConsulta}>
                                    Agendar consulta
                                </Button>
                            </div>
                        </div>
                    </div>
                ))}

                <div className="pet-add-card-slot" onClick={onCadastrarOutroPet}>
                    <div className="pet-add-slot-circle">+</div>
                    <h3 style={{ margin: '0 0 6px 0', fontWeight: 800 }}>Cadastrar outro pet</h3>
                    <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--color-neutral-muted)' }}>
                        Adicione animais para centralizar vacinas e consultas.
                    </p>
                    <div style={{ marginTop: '16px' }}>
                        <Button variant="outlined" size="sm" type="button" onClick={onCadastrarOutroPet}>
                            + Cadastrar pet
                        </Button>
                    </div>
                </div>
            </div>

            {/* Modal Interativo da Carteira de Vacinação */}
            {modalCarteiraAberto && (
                <div className="modal-overlay">
                    <div className="modal-carteira-card">
                        <div className="carteira-header">
                            <div>
                                <span className="tutor-pill-tag" style={{ marginBottom: '6px' }}>💉 Carteira Digital</span>
                                <h2 style={{ margin: '4px 0 0 0', fontSize: '1.4rem', fontWeight: 800 }}>
                                    Carteira de Vacinação & Prevenção
                                </h2>
                                <span style={{ fontSize: '0.82rem', color: 'var(--color-neutral-muted)' }}>
                  Acompanhe imunizações, reforços e histórico registrado pela clínica.
                </span>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setModalCarteiraAberto(false)}
                            >
                                ✕
                            </button>
                        </div>

                        <div className="carteira-tabs">
                            <button
                                type="button"
                                className={`carteira-tab-btn ${petFiltroCarteira === 'todos' ? 'active' : ''}`}
                                onClick={() => setPetFiltroCarteira('todos')}
                            >
                                Todos
                            </button>
                            <button
                                type="button"
                                className={`carteira-tab-btn ${petFiltroCarteira === 'mel' ? 'active' : ''}`}
                                onClick={() => setPetFiltroCarteira('mel')}
                            >
                                🐶 Mel (Lembrete)
                            </button>
                            <button
                                type="button"
                                className={`carteira-tab-btn ${petFiltroCarteira === 'thor' ? 'active' : ''}`}
                                onClick={() => setPetFiltroCarteira('thor')}
                            >
                                🐕 Thor
                            </button>
                            <button
                                type="button"
                                className={`carteira-tab-btn ${petFiltroCarteira === 'luna' ? 'active' : ''}`}
                                onClick={() => setPetFiltroCarteira('luna')}
                            >
                                🐈 Luna
                            </button>
                        </div>

                        <div style={{ maxHeight: '340px', overflowY: 'auto', paddingRight: '6px' }}>
                            {imunizacoesExibidas.map((vac) => (
                                <div key={vac.id} className="vaccine-item-card">
                                    <div>
                                        <h4 className="vaccine-item-title">{vac.nome}</h4>
                                        <p className="vaccine-item-sub">
                                            Paciente: <strong>{vac.pet}</strong> • Dose: {vac.dose}
                                        </p>
                                        <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>
                      Previsão / Reforço: <strong>{vac.previsao}</strong> • Lote: {vac.lote}
                    </span>
                                    </div>
                                    <div>
                                        <Badge variant={vac.status === 'EM DIA' ? 'success' : 'warning'}>
                                            {vac.status === 'EM DIA' ? '✓ Em dia' : '● Pendente'}
                                        </Badge>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '24px', paddingTop: '16px', borderTop: '1px solid var(--color-surface-soft)' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>
                Emitido digitalmente por Pet Cuidar Clínica Veterinária
              </span>
                            <div style={{ display: 'flex', gap: '10px' }}>
                                <Button variant="outlined" size="sm" onClick={() => setModalCarteiraAberto(false)}>
                                    Fechar
                                </Button>
                                <Button variant="primary" size="sm" onClick={() => { setModalCarteiraAberto(false); onAgendarConsulta(); }}>
                                    📅 Agendar Vacina
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}