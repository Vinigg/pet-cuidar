import React, { useState } from 'react';
import { solicitarCodigo, verificarCodigo } from '../api/api';
import { Button } from './common/UIComponents';

export default function Login({ finalidade, onVerificado, onAlternar, erroSessao }) {
    const [email, setEmail] = useState('');
    const [codigo, setCodigo] = useState('');
    const [aguardandoCodigo, setAguardandoCodigo] = useState(false);
    const [ocupado, setOcupado] = useState(false);
    const [mensagem, setMensagem] = useState('');
    const [erro, setErro] = useState('');
    const cadastro = finalidade === 'CADASTRO';

    const executar = async (acao, fallback) => {
        setOcupado(true);
        setErro('');
        try {
            await acao();
        } catch (e) {
            setErro(e.response?.data?.mensagem || fallback);
        } finally {
            setOcupado(false);
        }
    };

    const pedir = () => executar(async () => {
        const { data } = await solicitarCodigo(email, finalidade);
        setMensagem(data.mensagem);
        setAguardandoCodigo(true);
    }, 'Não foi possível solicitar o código. Tente novamente.');

    const enviar = (e) => {
        e.preventDefault();
        if (!aguardandoCodigo) {
            pedir();
            return;
        }
        executar(async () => {
            const { data } = await verificarCodigo(email, finalidade, codigo);
            onVerificado(data, email.trim().toLowerCase());
        }, 'Não foi possível verificar o código. Tente novamente.');
    };

    return (
        <div className="login-page-wrapper">
            <div className="login-card-container">
                <aside className="login-left-banner">
                    <div className="navbar-brand">
                        <span className="navbar-paw" aria-hidden="true">🐾</span>
                        <span className="navbar-logo-text" style={{ color: 'var(--color-primary)' }}>Pet Cuidar</span>
                    </div>
                    <div className="login-hero-illustration" aria-hidden="true">
                        <div className="login-hero-img-box">🐕🐱</div>
                    </div>
                    <h1 className="login-headline-title">Cuidar do seu pet ficou mais fácil.</h1>
                </aside>
                <main className="login-right-form-col">
                    <h2 className="login-form-title">{cadastro ? 'Criar conta de tutor' : 'Entrar'}</h2>
                    <p className="login-form-subtitle">
                        {aguardandoCodigo ? 'Digite o código de 8 dígitos recebido por e-mail.'
                            : 'Informe seu e-mail para receber um código de acesso.'}
                    </p>
                    {mensagem && <p role="status">{mensagem}</p>}
                    {(erro || erroSessao) && <p className="alert-box error" role="alert">{erro || erroSessao}</p>}
                    <form onSubmit={enviar}>
                        <div className="login-inputs-stack">
                            <div className="input-group">
                                <label className="input-label" htmlFor="email-acesso">E-mail</label>
                                <input className="select-custom" id="email-acesso" type="email" autoComplete="email"
                                    value={email} onChange={(e) => setEmail(e.target.value)}
                                    readOnly={aguardandoCodigo} maxLength={254} required />
                            </div>
                            {aguardandoCodigo && (
                                <div className="input-group">
                                    <label className="input-label" htmlFor="codigo-acesso">Código de acesso</label>
                                    <input className="select-custom" id="codigo-acesso" type="text" autoComplete="one-time-code"
                                        inputMode="numeric" pattern="[0-9]{8}" maxLength={8}
                                        value={codigo} onChange={(e) => setCodigo(e.target.value)} required />
                                </div>
                            )}
                        </div>
                        <Button type="submit" size="lg" disabled={ocupado} style={{ width: '100%' }}>
                            {ocupado ? 'Aguarde…' : aguardandoCodigo ? 'Verificar código' : 'Enviar código por e-mail'}
                        </Button>
                        {aguardandoCodigo && (
                            <div className="form-actions-footer">
                                <Button type="button" variant="outlined" onClick={() => {
                                    setAguardandoCodigo(false);
                                    setCodigo('');
                                    setMensagem('');
                                    setErro('');
                                }} disabled={ocupado}>Alterar e-mail</Button>
                                <Button type="button" variant="outlined" disabled={ocupado} onClick={pedir}>
                                    Reenviar código
                                </Button>
                            </div>
                        )}
                    </form>
                    <Button type="button" variant="outlined" disabled={ocupado} onClick={onAlternar}
                        style={{ width: '100%', marginTop: 16 }}>
                        {cadastro ? 'Voltar à entrada' : 'Criar conta de tutor'}
                    </Button>
                </main>
            </div>
        </div>
    );
}
