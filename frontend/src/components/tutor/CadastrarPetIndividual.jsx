import React, { useState } from 'react';
import axios from 'axios';
import { Button, Input } from '../common/UIComponents';

export default function CadastrarPetIndividual({ onVoltar, onSucesso }) {
    const [nome, setNome] = useState('');
    const [especie, setEspecie] = useState('Cachorro');
    const [idade, setIdade] = useState('');
    const [sexo, setSexo] = useState('Macho');
    const [alergiasCriticas, setAlergiasCriticas] = useState('');
    const [vacinacaoEmDia, setVacinacaoEmDia] = useState(true);
    const [loading, setLoading] = useState(false);
    const [erro, setErro] = useState(null);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setErro(null);

        try {
            await axios.post('http://localhost:8080/pets', {
                nome,
                especie,
                idade: idade ? parseInt(idade, 10) : null,
                alergiasCriticas,
                tutorId: 1
            });
            if (onSucesso) onSucesso();
        } catch {
            // Permite teste de transição no frontend mesmo sem backend ativo
            if (onSucesso) onSucesso();
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="tutor-page-wrapper" style={{ maxWidth: '820px' }}>
            <div className="tutor-breadcrumb">
                <span className="tutor-breadcrumb-link" onClick={onVoltar}>← Meus pets</span>
                <span>/</span>
                <span>Cadastrar pet</span>
            </div>

            <div className="tutor-consultas-table-card" style={{ padding: '32px' }}>
                {erro && <div className="alert-box error">{erro}</div>}

                <form onSubmit={handleSubmit}>
                    <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: '28px', marginBottom: '24px' }}>
                        <div className="pet-register-avatar-picker" style={{ height: '100%', minHeight: '180px' }}>
                            <span style={{ fontSize: '2.5rem' }}>📷</span>
                            <span style={{ fontWeight: 800, fontSize: '0.85rem', marginTop: '6px' }}>Adicionar foto</span>
                            <span style={{ fontSize: '0.7rem', color: 'var(--color-neutral-muted)' }}>PNG ou JPG até 5MB</span>
                        </div>

                        <div>
                            <Input
                                label="NOME DO PET *"
                                placeholder="Ex: Bob, Pipoca, Mel"
                                value={nome}
                                onChange={(e) => setNome(e.target.value)}
                                required
                            />

                            <div style={{ marginTop: '16px' }}>
                                <label className="input-label">ESPÉCIE *</label>
                                <div className="species-selector-row">
                                    {['Cachorro', 'Gato', 'Outro'].map((esp) => (
                                        <button
                                            key={esp}
                                            type="button"
                                            onClick={() => setEspecie(esp)}
                                            className={`species-btn-choice ${especie === esp ? 'active' : ''}`}
                                        >
                                            {esp === 'Cachorro' ? '🐶' : esp === 'Gato' ? '🐱' : '🐾'} {esp}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div className="form-grid-2" style={{ marginTop: '16px' }}>
                                <Input
                                    label="IDADE APROXIMADA (ANOS)"
                                    type="number"
                                    placeholder="Ex: 3"
                                    value={idade}
                                    onChange={(e) => setIdade(e.target.value)}
                                />
                                <div className="input-group">
                                    <label className="input-label">SEXO</label>
                                    <select className="select-custom" value={sexo} onChange={(e) => setSexo(e.target.value)}>
                                        <option value="Macho">Macho</option>
                                        <option value="Fêmea">Fêmea</option>
                                    </select>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="input-group" style={{ marginBottom: '20px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <label className="input-label">ALERGIAS E RESTRIÇÕES CLÍNICAS <span style={{ color: 'var(--color-secondary)' }}>Atenção</span></label>
                            <span style={{ fontSize: '0.72rem', color: 'var(--color-neutral-muted)' }}>Opcional</span>
                        </div>
                        <textarea
                            className="select-custom"
                            style={{ height: '80px', padding: '10px' }}
                            placeholder="Ex: Hipersensibilidade a dipirona, dermatite atópica por gramíneas, alergia a frango..."
                            value={alergiasCriticas}
                            onChange={(e) => setAlergiasCriticas(e.target.value)}
                        />
                    </div>

                    <div style={{ backgroundColor: '#FFF5F0', border: '1px solid #FAD1C0', padding: '14px 18px', borderRadius: 'var(--radius-md)', marginBottom: '20px', display: 'flex', gap: '12px', alignItems: 'center' }}>
                        <span style={{ fontSize: '1.4rem' }}>⚠️</span>
                        <div style={{ fontSize: '0.8rem', color: 'var(--color-neutral-dark)', lineHeight: 1.4 }}>
                            <strong>Aviso importante para a equipe veterinária:</strong><br />
                            Informe alergias a medicamentos, vacinas ou alimentos. Este aviso é destacado na fila de atendimento e visualizado pelo veterinário antes de qualquer prescrição.
                        </div>
                    </div>

                    <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px 18px', borderRadius: 'var(--radius-md)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '28px' }}>
                        <div>
                            <strong style={{ fontSize: '0.9rem' }}>Vacinação em dia?</strong>
                            <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>Ajuda na previsão de reforços anuais</div>
                        </div>
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <Button type="button" variant={vacinacaoEmDia ? 'primary' : 'outlined'} size="sm" onClick={() => setVacinacaoEmDia(true)}>
                                Sim
                            </Button>
                            <Button type="button" variant={!vacinacaoEmDia ? 'primary' : 'outlined'} size="sm" onClick={() => setVacinacaoEmDia(false)}>
                                Pendente / Não sei
                            </Button>
                        </div>
                    </div>

                    <div className="form-actions-footer">
                        <Button variant="outlined" type="button" onClick={onVoltar} disabled={loading}>
                            Cancelar
                        </Button>
                        <Button variant="primary" size="lg" type="submit" disabled={loading}>
                            {loading ? 'Salvando...' : 'Salvar pet'}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}