import React from 'react';

export default function Navbar({ abaAtiva, setAbaAtiva }) {
    const abas = [
        { id: 'cadastro', label: 'Cadastro' },
        { id: 'agendamento', label: 'Agendar consulta' },
        { id: 'painel', label: 'Painel do dia' },
        { id: 'historico', label: 'Histórico do pet' },
    ];

    return (
        <header className="navbar">
            <div className="navbar-brand">
                <span className="navbar-paw">🐾</span>
                <span className="navbar-logo-text">Pet Cuidar</span>
            </div>

            <nav className="navbar-links">
                {abas.map((item) => (
                    <button
                        key={item.id}
                        type="button"
                        onClick={() => setAbaAtiva(item.id)}
                        className={`navbar-tab ${abaAtiva === item.id ? 'active' : ''}`}
                    >
                        {item.label}
                    </button>
                ))}
            </nav>
        </header>
    );
}