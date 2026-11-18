import React, { useState } from 'react';
import { Button, Input } from './common/UIComponents';

export default function Login({ onLogin, onCadastrarTutor }) {
    const [role, setRole] = useState('tutor');
    const [email, setEmail] = useState('');
    const [senha, setSenha] = useState('');
    const [lembrar, setLembrar] = useState(true);

    const handleSubmit = (e) => {
        e.preventDefault();
        onLogin({
            role: role,
            nome: role === 'tutor' ? 'Mariana Costa' : 'Dra. Ana Souza',
            email: email || (role === 'tutor' ? 'mariana@email.com' : 'ana.souza@petcuidar.com')
        });
    };

    return (
        <div className="login-page-wrapper">
            <div className="login-card-container">
                <aside className="login-left-banner">
                    <div className="login-left-top">
                        <div className="navbar-brand">
                            <span className="navbar-paw">🐾</span>
                            <span className="navbar-logo-text" style={{ color: 'var(--color-primary)' }}>Pet Cuidar</span>
                        </div>
                        <span className="login-tag-safe">● Portal Seguro</span>
                    </div>

                    <div className="login-hero-illustration">
                        <div className="login-hero-img-box">
                            🐕🐱
                        </div>
                        <div className="login-hero-badge-floating">
                            <span style={{ fontSize: '1.2rem' }}>🩺</span>
                            <div>
                                <p className="login-floating-title">Acolhimento & Saúde</p>
                                <p className="login-floating-desc">Cuidado humanizado e contínuo</p>
                            </div>
                        </div>
                    </div>

                    <div>
                        <h1 className="login-headline-title">
                            Cuidar do seu pet ficou mais fácil.
                        </h1>
                        <p className="login-headline-desc">
                            Agende consultas, acompanhe vacinas e garanta a saúde e o bem-estar do seu melhor amigo em um só lugar.
                        </p>

                        <div className="login-pill-chips-row">
                            <span className="login-chip-feature">🩺 +12.000 pets atendidos</span>
                            <span className="login-chip-feature">💬 Lembretes via WhatsApp</span>
                            <span className="login-chip-feature">📋 Histórico Clínico Unificado</span>
                        </div>
                    </div>
                </aside>

                <main className="login-right-form-col">
                    <span className="login-welcome-tag">🧡 BEM-VINDO(A) DE VOLTA</span>
                    <h2 className="login-form-title">Entrar</h2>
                    <p className="login-form-subtitle">
                        Acesse sua conta para gerenciar agendamentos e prontuários com rapidez e afeto.
                    </p>

                    <div className="role-toggle-tabs">
                        <button
                            type="button"
                            className={`role-toggle-btn ${role === 'tutor' ? 'active' : ''}`}
                            onClick={() => setRole('tutor')}
                        >
                            🐾 Tutor
                        </button>
                        <button
                            type="button"
                            className={`role-toggle-btn ${role === 'clinica' ? 'active' : ''}`}
                            onClick={() => setRole('clinica')}
                        >
                            🏥 Equipe Clínica
                        </button>
                    </div>

                    <form onSubmit={handleSubmit}>
                        <div className="login-inputs-stack">
                            <Input
                                label="E-MAIL"
                                type="email"
                                placeholder={role === 'tutor' ? 'mariana@email.com' : 'clinica@petcuidar.com'}
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                            />

                            <Input
                                label="SENHA"
                                type="password"
                                placeholder="••••••••"
                                value={senha}
                                onChange={(e) => setSenha(e.target.value)}
                                required
                            />
                        </div>

                        <div className="login-options-row">
                            <label className="login-remember-me">
                                <input
                                    type="checkbox"
                                    checked={lembrar}
                                    onChange={(e) => setLembrar(e.target.checked)}
                                />
                                Lembrar de mim por 30 dias
                            </label>
                            <span className="login-forgot-link">Esqueci minha senha</span>
                        </div>

                        <Button variant="primary" size="lg" style={{ width: '100%' }} type="submit">
                            Entrar no Pet Cuidar →
                        </Button>

                        <div className="login-divider-text">
                            <span>OU</span>
                        </div>

                        <Button
                            variant="outlined"
                            size="md"
                            style={{ width: '100%' }}
                            type="button"
                            onClick={onCadastrarTutor}
                        >
                            👤+ Cadastrar novo tutor ou pet
                        </Button>
                    </form>

                    <p className="login-footer-disclaimer">
                        Ao continuar, você concorda com nossos Termos de Uso e Política de Privacidade.<br />
                        🚑 Precisa de ajuda emergencial? Central 24h: <strong>0800 738 2843</strong>
                    </p>
                </main>
            </div>
        </div>
    );
}