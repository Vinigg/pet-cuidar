import React from 'react';

export default function Navbar({ abaAtiva, setAbaAtiva, usuario, onLogout }) {
    const abas = [
        { id: 'painel', label: 'Painel do dia' },
        { id: 'agenda-semanal', label: 'Agenda' },
        { id: 'tutores-pets', label: 'Tutores e pets' },
        { id: 'lembretes', label: 'Lembretes' },
        { id: 'historico', label: 'Prontuário' },
        { id: 'veterinarios', label: 'Veterinários' }
    ];

    return (
        <header className="navbar">
            <div className="navbar-brand">
                <span className="navbar-paw">🐾</span>
                <div>
                    <span className="navbar-logo-text">Pet Cuidar</span>
                    <span style={{ display: 'block', fontSize: '0.65rem', color: 'rgba(255, 255, 255, 0.75)', fontWeight: 600 }}>
            Painel da Recepção
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
                    <div className="user-avatar-circle" style={{ backgroundColor: 'rgba(255, 255, 255, 0.2)', color: '#FFFFFF' }}>
                        🩺
                    </div>
                    <div className="user-meta-name">
            <span className="user-name-title" style={{ color: '#FFFFFF' }}>
              {usuario?.nome || 'Dra. Ana Souza'}
            </span>
                        <span className="user-role-subtitle" style={{ color: 'rgba(255, 255, 255, 0.75)' }}>
              Recepção & Clínica
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