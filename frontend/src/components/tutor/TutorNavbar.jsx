import React from 'react';

export default function TutorNavbar({ abaTutor, setAbaTutor, usuario, onLogout }) {
    return (
        <header className="tutor-navbar">
            <div className="navbar-brand">
                <span className="navbar-paw">🐾</span>
                <div>
                    <span className="navbar-logo-text" style={{ color: 'var(--color-primary)' }}>Pet Cuidar</span>
                    <span style={{ display: 'block', fontSize: '0.65rem', color: 'var(--color-neutral-muted)', fontWeight: 600 }}>
            Área do Tutor
          </span>
                </div>
            </div>

            <nav className="tutor-nav-links">
                <button
                    type="button"
                    onClick={() => setAbaTutor('inicio')}
                    className={`tutor-nav-item ${abaTutor === 'inicio' ? 'active' : ''}`}
                >
                    Início
                </button>
                <button
                    type="button"
                    onClick={() => setAbaTutor('meus-pets')}
                    className={`tutor-nav-item ${abaTutor === 'meus-pets' || abaTutor === 'cadastrar-pet' ? 'active' : ''}`}
                >
                    Meus pets
                </button>
                <button
                    type="button"
                    onClick={() => setAbaTutor('consultas')}
                    className={`tutor-nav-item ${abaTutor === 'consultas' || abaTutor === 'detalhes-consulta' ? 'active' : ''}`}
                >
                    Consultas
                </button>
            </nav>

            <div className="tutor-nav-user">
                <div className="user-badge-profile">
                    <div className="user-avatar-circle">{usuario?.nome ? usuario.nome.charAt(0) : 'M'}</div>
                    <div className="user-meta-name">
                        <span className="user-name-title">{usuario?.nome || 'Mariana Costa'}</span>
                        <span className="user-role-subtitle">Tutora</span>
                    </div>
                </div>
                <button
                    type="button"
                    onClick={onLogout}
                    className="btn-logout"
                    style={{ color: 'var(--color-neutral-dark)', borderColor: 'var(--color-neutral-border)' }}
                >
                    Sair
                </button>
            </div>
        </header>
    );
}