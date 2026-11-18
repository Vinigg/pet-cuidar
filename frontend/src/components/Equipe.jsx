import React, { useEffect, useState } from 'react';
import { cadastrarColaborador, listarEquipe, listarVeterinarios, vincularVeterinario } from '../api/api';
import { Button } from './common/UIComponents';

export default function Equipe() {
    const [equipe, setEquipe] = useState([]);
    const [veterinarios, setVeterinarios] = useState([]);
    const [veterinarioId, setVeterinarioId] = useState('');
    const [emailVet, setEmailVet] = useState('');
    const [nome, setNome] = useState('');
    const [email, setEmail] = useState('');
    const [ocupado, setOcupado] = useState(false);
    const [carregando, setCarregando] = useState(true);
    const [erro, setErro] = useState('');
    const [mensagem, setMensagem] = useState('');

    useEffect(() => {
        let ativo = true;
        Promise.all([listarEquipe(), listarVeterinarios({ ativo: true }), listarVeterinarios({ ativo: false })])
            .then(([contas, ativos, inativos]) => {
                if (ativo) {
                    setEquipe(contas.data);
                    setVeterinarios([...ativos.data, ...inativos.data]);
                }
            })
            .catch(() => { if (ativo) setErro('Não foi possível carregar as contas da equipe.'); })
            .finally(() => { if (ativo) setCarregando(false); });
        return () => { ativo = false; };
    }, []);

    const salvar = async (e, acao) => {
        e.preventDefault();
        setOcupado(true);
        setErro('');
        setMensagem('');
        try {
            const { data } = await acao();
            setEquipe((atual) => [...atual.filter((membro) => membro.id !== data.id), data]);
            setMensagem(data.perfil === 'COLABORADOR' ? 'Colaborador cadastrado.' : 'E-mail do veterinário vinculado.');
            if (data.perfil === 'COLABORADOR') {
                setNome('');
                setEmail('');
            }
        } catch (e) {
            setErro(e.response?.data?.mensagem || 'Não foi possível salvar a conta.');
        } finally {
            setOcupado(false);
        }
    };

    return (
        <div className="form-container">
            <div className="form-card">
                <h1 className="form-title">Contas da equipe</h1>
                {erro && <p className="alert-box error" role="alert">{erro}</p>}
                {mensagem && <p className="alert-box success" role="status">{mensagem}</p>}
                <form onSubmit={(e) => salvar(e, () => cadastrarColaborador({ nome, email }))}>
                    <h2 className="form-section-title">Cadastrar colaborador</h2>
                    <div className="form-grid-2">
                        <div className="input-group">
                            <label htmlFor="nome-colaborador" className="input-label">Nome do colaborador</label>
                            <input id="nome-colaborador" className="select-custom" value={nome}
                                onChange={(e) => setNome(e.target.value)} required maxLength={255} />
                        </div>
                        <div className="input-group">
                            <label htmlFor="email-colaborador" className="input-label">E-mail do colaborador</label>
                            <input type="email" id="email-colaborador" className="select-custom" value={email}
                                onChange={(e) => setEmail(e.target.value)} required maxLength={254} />
                        </div>
                    </div>
                    <Button type="submit" disabled={ocupado}>Cadastrar colaborador</Button>
                </form>
                <hr className="form-divider" />
                <form onSubmit={(e) => salvar(e, () => vincularVeterinario(veterinarioId, emailVet))}>
                    <h2 className="form-section-title">Vincular acesso de veterinário</h2>
                    <div className="form-grid-2">
                        <div className="input-group">
                            <label htmlFor="veterinario-conta" className="input-label">Veterinário</label>
                            <select id="veterinario-conta" className="select-custom" value={veterinarioId}
                                required disabled={carregando} onChange={(e) => {
                                    const id = e.target.value;
                                    setVeterinarioId(id);
                                    setEmailVet(equipe.find((m) => String(m.veterinarioId) === id)?.email || '');
                                }}>
                                <option value="">Selecione</option>
                                {veterinarios.map((vet) => <option key={vet.id} value={vet.id}>{vet.nome}</option>)}
                            </select>
                        </div>
                        <div className="input-group">
                            <label htmlFor="email-veterinario" className="input-label">E-mail do veterinário</label>
                            <input type="email" id="email-veterinario" className="select-custom" value={emailVet}
                                onChange={(e) => setEmailVet(e.target.value)} required maxLength={254} />
                        </div>
                    </div>
                    <Button type="submit" disabled={ocupado || !veterinarioId}>Vincular e-mail</Button>
                </form>
                <hr className="form-divider" />
                <h2 className="form-section-title">Acessos cadastrados</h2>
                {carregando && <p role="status">Carregando contas…</p>}
                {!carregando && equipe.length === 0 && <p>Nenhuma conta cadastrada.</p>}
                <ul>
                    {equipe.map((membro) => <li key={membro.id}>{membro.nome} · {membro.email} ·
                        {membro.perfil === 'COLABORADOR' ? ' Colaborador' : ' Veterinário'}</li>)}
                </ul>
            </div>
        </div>
    );
}
