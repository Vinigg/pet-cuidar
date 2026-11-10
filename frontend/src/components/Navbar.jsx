import React from 'react';

export default function Navbar({ abaAtiva, setAbaAtiva, usuario, onLogout }) {
    const abas = [
        { id: 'painel', label: 'Painel do dia' },
        { id: 'agendamento', label: 'Agendar consulta' },
        { id: 'cadastro', label: 'Cadastro' },
        { id: 'historico', label: 'Histórico do pet' }
    ];

    return (
        <header className="navbar">
            <div className="navbar-brand">
                <span className="navbar-paw">🐾</span>
                <div>
                    <span className="navbar-logo-text">Pet Cuidar</span>
                    <span style={{ display: 'block', fontSize: '0.65rem', color: 'rgba(255,255,255,0.7)', fontWeight: 600 }}>
            Recepção & Clínica
          </span>
                </div>
            </div>

            <nav className="navbar-links">
                {abas.map((item) => (
                    <button
                        key={item.id}
                        type="button"
                        onClick={() => setAbaAtiva && setAbaAtiva(item.id)}
                        className={`navbar-tab ${abaAtiva === item.id ? 'active' : ''}`}
                    >
                        {item.label}
                    </button>
                ))}
            </nav>

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#FFFFFF' }}>
                    <div className="user-avatar-circle" style={{ backgroundColor: 'rgba(255,255,255,0.2)', color: '#FFFFFF' }}>
                        🩺
                    </div>
                    <div className="user-meta-name">
            <span className="user-name-title" style={{ color: '#FFFFFF' }}>
              {usuario?.nome || 'Equipe Clínica'}
            </span>
                        <span className="user-role-subtitle" style={{ color: 'rgba(255,255,255,0.7)' }}>
              Profissional
            </span>
                    </div>
                </div>
                <button type="button" onClick={onLogout} className="btn-logout">
                    Sair
                </button>
            </div>
        </header>
    );
}