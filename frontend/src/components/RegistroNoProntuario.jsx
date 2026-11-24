import React, { useRef, useState } from 'react';
import { registrarAtendimento } from '../api/api';
import { Button } from './common/UIComponents';
import { dataHora } from './tutor/TutorPresentation';

export default function RegistroNoProntuario({ petId, petNome, veterinarioId, consultas, onSalvo }) {
    const [consultaId, setConsultaId] = useState('');
    const [tipo, setTipo] = useState('Consulta de retorno');
    const [descricao, setDescricao] = useState('');
    const [erro, setErro] = useState('');
    const [sucesso, setSucesso] = useState('');
    const [salvando, setSalvando] = useState(false);
    const ocupado = useRef(false);
    const opcoes = consultas.filter(c => String(c.veterinarioId) === String(veterinarioId) && ['AGENDADA', 'CONFIRMADA', 'REALIZADA'].includes(c.status));
    const enviar = async e => {
        e.preventDefault();
        if (ocupado.current) return;
        const texto = `${tipo}\n\n${descricao.trim()}`;
        if (!opcoes.some(c => String(c.consultaId) === consultaId) || descricao.trim().length < 10 || texto.length > 2000) {
            setErro('Selecione uma consulta sua e informe de 10 a 2000 caracteres, incluindo o tipo de atendimento.');
            return;
        }
        ocupado.current = true; setSalvando(true); setErro(''); setSucesso('');
        try {
            await registrarAtendimento({ petId, consultaId: Number(consultaId), veterinarioId, descricaoClinica: texto });
            setDescricao(''); setSucesso('Atendimento registrado no prontuário.');
            onSalvo();
        } catch (e) {
            setErro(e.response?.data?.mensagem || 'Não foi possível registrar o atendimento.');
        } finally { ocupado.current = false; setSalvando(false); }
    };
    return <form onSubmit={enviar}>
        <h2 style={{ fontSize: '1.1rem' }}>Registrar atendimento</h2><p>Novo registro no prontuário de {petNome}.</p>
        {sucesso && <p role="status" className="alert-box success">{sucesso}</p>}
        {erro && <p role="alert" className="alert-box error">{erro}</p>}
        <div className="restored-action-stack">
            <label className="input-label">CONSULTA<select className="select-custom" value={consultaId} onChange={e => setConsultaId(e.target.value)} disabled={salvando} required><option value="">Selecione sua consulta</option>{opcoes.map(c => <option key={c.consultaId} value={c.consultaId}>{dataHora(c.dataHora)}</option>)}</select></label>
            {!opcoes.length && <p>Nenhuma consulta elegível vinculada a você neste paciente.</p>}
            <label className="input-label">TIPO DE ATENDIMENTO<select className="select-custom" value={tipo} disabled={salvando} onChange={e => setTipo(e.target.value)}><option>Consulta de retorno</option><option>Consulta de rotina / Check-up</option><option>Atendimento emergencial</option></select></label>
            <label className="input-label">EVOLUÇÃO CLÍNICA & ANOTAÇÕES<textarea className="textarea-custom" placeholder="Descreva a evolução clínica..." value={descricao} onChange={e => setDescricao(e.target.value)} disabled={salvando} minLength={10} maxLength={2000 - tipo.length - 2} required /></label>
            <Button type="submit" disabled={salvando || !opcoes.length}>{salvando ? 'Salvando...' : '✓ Salvar atendimento'}</Button>
        </div>
    </form>;
}
