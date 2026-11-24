import React from 'react';

export default function Navbar({ abaAtiva, setAbaAtiva, usuario, abas = [], perfil, onLogout, saindo }) {
    return (
        <header className="navbar">
            <div className="navbar-brand">
                <span className="navbar-paw" aria-hidden="true">🐾</span>
                <div>
                    <span className="navbar-logo-text">Pet Cuidar</span>
                    <span className="user-role-subtitle" style={{ display: 'block', color: 'rgba(255,255,255,.75)' }}>
                        {perfil}
                    </span>
                </div>
            </div>
            <nav className="navbar-links" aria-label={`Navegação de ${perfil}`}>
                {abas.map(({ id, label }) => (
                    <button key={id} type="button" onClick={() => setAbaAtiva(id)}
                        className={`navbar-tab ${abaAtiva === id ? 'active' : ''}`}
                        aria-current={abaAtiva === id ? 'page' : undefined}>
                        {label}
                    </button>
                ))}
            </nav>
            <div className="navbar-user-actions">
                <div className="navbar-user-info">
                    <div className="user-avatar-circle" aria-hidden="true">🩺</div>
                    <div className="user-meta-name">
                        <span className="user-name-title" style={{ color: '#fff' }}>{usuario?.nome || usuario?.email}</span>
                        <span className="user-role-subtitle" style={{ color: 'rgba(255,255,255,.75)' }}>{perfil}</span>
                    </div>
                </div>
                <button type="button" onClick={onLogout} disabled={saindo} className="btn-logout">
                    {saindo ? 'Saindo…' : 'Sair'}
                </button>
            </div>
        </header>
    );
}
