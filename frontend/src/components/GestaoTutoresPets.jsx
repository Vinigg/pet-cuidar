import React, { useState } from 'react';
import { Button, Badge, Input } from './common/UIComponents';

const TUTORES_BASE = [
    {
        id: 1,
        nome: 'Mariana Silva',
        tel: '(11) 98765-4321',
        email: 'mariana.silva@email.com',
        pets: ['Thor (Cão)', 'Luna (Gato)'],
        faltas: 0,
        petPrincipal: { nome: 'Thor', especie: 'Cachorro', raca: 'Golden Retriever', idade: '4 anos', peso: '32,4 kg', microchip: '981.098.223.109', vacinas: 'Em dia' }
    },
    {
        id: 2,
        nome: 'Juliana Faria',
        tel: '(11) 98765-4321',
        email: 'juliana.faria@email.com',
        pets: ['Simba (Gato)'],
        faltas: 2,
        petPrincipal: { nome: 'Simba', especie: 'Gato Persa', raca: 'Persa Tradicional', idade: '3 anos', peso: '4,8 kg', microchip: '981.042.871.405', vacinas: 'Em dia' }
    },
    {
        id: 3,
        nome: 'Rodrigo Alencar',
        tel: '(11) 97123-8899',
        email: 'rodrigo.alencar@email.com',
        pets: ['Billy (Cão)'],
        faltas: 1,
        petPrincipal: { nome: 'Billy', especie: 'Cachorro', raca: 'Buldogue Francês', idade: '3 anos', peso: '12,1 kg', microchip: '981.884.201.774', vacinas: 'Atrasada' }
    },
    {
        id: 4,
        nome: 'Fernanda Costa',
        tel: '(11) 96543-1122',
        email: 'fernanda.costa@email.com',
        pets: ['Pipoca (Cão)'],
        faltas: 0,
        petPrincipal: { nome: 'Pipoca', especie: 'Cachorro', raca: 'Poodle Toy', idade: '6 anos', peso: '5,2 kg', microchip: '981.002.441.982', vacinas: 'Em dia' }
    }
];

export default function GestaoTutoresPets({ onVerProntuario, onNovoTutor }) {
    const [tutores] = useState(TUTORES_BASE);
    const [tutorSelecionado, setTutorSelecionado] = useState(TUTORES_BASE[0]);
    const [busca, setBusca] = useState('');

    const tutoresFiltrados = tutores.filter((t) =>
        t.nome.toLowerCase().includes(busca.toLowerCase()) ||
        t.email.toLowerCase().includes(busca.toLowerCase()) ||
        t.pets.some(p => p.toLowerCase().includes(busca.toLowerCase()))
    );

    return (
        <div className="clinica-container">
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <div>
          <span style={{ fontSize: '0.75rem', fontWeight: 800, color: 'var(--color-primary)' }}>
            RECEPÇÃO • BASE DE PACIENTES
          </span>
                    <h1 className="tutor-main-title" style={{ fontSize: '2rem', margin: '4px 0' }}>Gestão Cadastral e Histórico</h1>
                    <p className="tutor-main-sub">Painel de assiduidade, prontuários integrados e verificação de tutores.</p>
                </div>
                <Button variant="primary" size="sm" onClick={() => onNovoTutor ? onNovoTutor() : alert('Abrindo cadastro de novo tutor')}>
                    + Novo tutor
                </Button>
            </header>

            <div className="recepcao-dashboard-grid">
                <div className="table-card">
                    <Input
                        placeholder="Buscar tutor, pet ou prontuário..."
                        value={busca}
                        onChange={(e) => setBusca(e.target.value)}
                    />

                    <table className="panel-table" style={{ marginTop: '16px' }}>
                        <thead>
                        <tr>
                            <th>TUTOR</th>
                            <th>CONTATO</th>
                            <th>PETS</th>
                            <th>FALTAS</th>
                            <th style={{ textAlign: 'right' }}>AÇÕES</th>
                        </tr>
                        </thead>
                        <tbody>
                        {tutoresFiltrados.map((t) => {
                            const isSelected = tutorSelecionado.id === t.id;
                            return (
                                <tr
                                    key={t.id}
                                    style={{ cursor: 'pointer', backgroundColor: isSelected ? 'var(--color-primary-light)' : 'transparent' }}
                                    onClick={() => setTutorSelecionado(t)}
                                >
                                    <td><strong>{t.nome}</strong></td>
                                    <td>
                                        <div>{t.tel}</div>
                                        <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>{t.email}</div>
                                    </td>
                                    <td>
                                        {t.pets.map((p) => (
                                            <span key={p} style={{ marginRight: '6px', fontSize: '0.8rem', backgroundColor: 'var(--color-surface-soft)', padding: '2px 8px', borderRadius: '4px' }}>
                          {p}
                        </span>
                                        ))}
                                    </td>
                                    <td>
                                        <Badge variant={t.faltas > 0 ? 'danger' : 'success'}>{t.faltas} faltas</Badge>
                                    </td>
                                    <td style={{ textAlign: 'right' }}>
                                        <Button
                                            variant={isSelected ? 'primary' : 'outlined'}
                                            size="sm"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setTutorSelecionado(t);
                                            }}
                                        >
                                            Ficha
                                        </Button>
                                    </td>
                                </tr>
                            );
                        })}
                        </tbody>
                    </table>
                </div>

                {tutorSelecionado && (
                    <aside className="table-card">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                            <div>
                                <h3 style={{ margin: '0 0 4px 0', fontSize: '1.2rem', fontWeight: 800 }}>{tutorSelecionado.nome}</h3>
                                <Badge variant="success">● Cadastro ativo</Badge>
                            </div>
                        </div>

                        <div style={{ margin: '16px 0', fontSize: '0.85rem', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            <div>📞 <strong>Telefone:</strong> {tutorSelecionado.tel}</div>
                            <div>✉️ <strong>E-mail:</strong> {tutorSelecionado.email}</div>
                        </div>

                        {tutorSelecionado.faltas > 0 && (
                            <div style={{ backgroundColor: '#FEECEB', border: '1px solid #FCD4D4', padding: '12px', borderRadius: 'var(--radius-md)', marginBottom: '16px', fontSize: '0.8rem', color: 'var(--color-danger)' }}>
                                <strong>⚠️ Atenção ao agendamento:</strong><br />
                                Possui {tutorSelecionado.faltas} falta(s) registrada(s). Recomenda-se confirmação ativa por WhatsApp.
                            </div>
                        )}

                        <h4 style={{ margin: '14px 0 8px 0', fontSize: '0.9rem', color: 'var(--color-neutral-dark)' }}>Pets vinculados</h4>
                        <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '14px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-neutral-border)' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <strong>{tutorSelecionado.petPrincipal.nome}</strong>
                                <Badge variant={tutorSelecionado.petPrincipal.vacinas === 'Em dia' ? 'success' : 'warning'}>
                                    Vacinas {tutorSelecionado.petPrincipal.vacinas}
                                </Badge>
                            </div>
                            <div style={{ fontSize: '0.8rem', color: 'var(--color-neutral-dark)', marginTop: '4px' }}>
                                {tutorSelecionado.petPrincipal.especie} • {tutorSelecionado.petPrincipal.raca} ({tutorSelecionado.petPrincipal.idade})
                            </div>
                            <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)', margin: '4px 0' }}>
                                Microchip: {tutorSelecionado.petPrincipal.microchip}
                            </div>
                            <Button
                                variant="primary"
                                size="sm"
                                style={{ width: '100%', marginTop: '10px' }}
                                onClick={() => onVerProntuario && onVerProntuario(tutorSelecionado.petPrincipal.nome)}
                            >
                                Ver prontuário completo →
                            </Button>
                        </div>
                    </aside>
                )}
            </div>
        </div>
    );
}