import React from 'react';

export default function Navbar({ abaAtiva, setAbaAtiva, usuario, onLogout, saindo, abas, perfil }) {

    return (
        <header className="navbar">
            <div className="navbar-brand">
                <span className="navbar-paw">🐾</span>
                <div>
                    <span className="navbar-logo-text">Pet Cuidar</span>
                    <span style={{ display: 'block', fontSize: '0.65rem', color: 'rgba(255,255,255,0.7)', fontWeight: 600 }}>
            {perfil}
          </span>
                </div>
            </div>

            <nav className="navbar-links">
                {abas.map((item) => (
                    <button
                        key={item.id}
                        type="button"
                        onClick={() => setAbaAtiva(item.id)}
                        className={`navbar-tab ${abaAtiva === item.id ? 'active' : ''}`}
                        aria-current={abaAtiva === item.id ? 'page' : undefined}
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
              {usuario.nome}
            </span>
                        <span className="user-role-subtitle" style={{ color: 'rgba(255,255,255,0.7)' }}>
              {perfil}
            </span>
                    </div>
                </div>
                <button type="button" onClick={onLogout} disabled={saindo} className="btn-logout">
                    Sair
                </button>
            </div>
        </header>
    );
}
