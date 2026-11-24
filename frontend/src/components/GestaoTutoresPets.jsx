import React, { useEffect, useState } from 'react';
import { listarTutores } from '../api/api';
import { Button, Badge, Input } from './common/UIComponents';
import { formatarTelefone } from '../utils/validacaoCadastro';

export default function GestaoTutoresPets({ onNovoTutor }) {
    const [tutores, setTutores] = useState([]);
    const [selecionado, setSelecionado] = useState(null);
    const [busca, setBusca] = useState('');
    const [erro, setErro] = useState('');
    const [carregando, setCarregando] = useState(true);
    const [tentativa, setTentativa] = useState(0);
    useEffect(() => {
        let ativo = true;
        setCarregando(true); setErro(''); setTutores([]); setSelecionado(null);
        listarTutores().then(({ data }) => { if (ativo) setTutores(data); })
            .catch(() => { if (ativo) setErro('Não foi possível carregar os tutores.'); })
            .finally(() => { if (ativo) setCarregando(false); });
        return () => { ativo = false; };
    }, [tentativa]);
    const filtrados = tutores.filter(t => [t.nome, t.email, ...t.pets.map(p => p.nome)].join(' ').toLowerCase().includes(busca.toLowerCase()));
    return <div className="clinica-container">
        <header className="panel-header"><div><span className="clinic-eyebrow">RECEPÇÃO • CADASTROS</span><h1 className="tutor-main-title">Tutores e Pets</h1><p className="tutor-main-sub">Dados de contato e companheiros cadastrados na clínica.</p></div><Button onClick={onNovoTutor}>+ Novo tutor</Button></header>
        <div className="clinic-toolbar"><Input aria-label="Buscar tutor ou pet" placeholder="Buscar tutor, e-mail ou pet..." value={busca} onChange={e => setBusca(e.target.value)} /><Button variant="outlined" disabled={carregando} onClick={() => setTentativa(v => v + 1)}>Atualizar cadastros</Button></div>
        {erro && <p className="alert-box error" role="alert">{erro}</p>}
        {carregando ? <p role="status">Carregando cadastros...</p> : <div className="recepcao-dashboard-grid">
            <section className="table-card clinic-table-scroll">{!filtrados.length ? <p className="empty-state">Nenhum tutor encontrado.</p> : <table className="panel-table"><thead><tr><th>TUTOR</th><th>PETS</th><th>AÇÕES</th></tr></thead><tbody>{filtrados.map(t => <tr key={t.id}><td><strong>{t.nome}</strong><div>{t.email}</div></td><td>{t.pets.map(p => p.nome).join(', ') || 'Nenhum pet'}</td><td><Button size="sm" variant={selecionado?.id === t.id ? 'primary' : 'outlined'} onClick={() => setSelecionado(t)}>Ficha</Button></td></tr>)}</tbody></table>}</section>
            <aside className="table-card">{selecionado ? <><h2>{selecionado.nome}</h2><Badge variant="primary">Cadastro da clínica</Badge><p>Telefone: {formatarTelefone(selecionado.telefone || '') || 'Não informado'}</p><p>E-mail: {selecionado.email}</p><h3>Pets vinculados</h3>{selecionado.pets.map(p => <article className="restored-recent-consult" key={p.id}><strong>{p.nome}</strong><p>{p.especie || 'Espécie não informada'}{p.idade != null && ` • ${p.idade} ano(s)`}</p></article>)}</> : <p>Selecione uma ficha para consultar contato e pets.</p>}</aside>
        </div>}
    </div>;
}
