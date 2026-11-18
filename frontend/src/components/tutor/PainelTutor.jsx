import React, { useEffect, useState } from 'react';
import { atualizarStatusConsulta, listarMeusPets, listarMinhasConsultas } from '../../api/api';
import AgendamentoForm from '../AgendamentoForm';
import { Button } from '../common/UIComponents';
import CadastrarPetIndividual from './CadastrarPetIndividual';
import ConsultaAgendadaSucesso from './ConsultaAgendadaSucesso';
import DetalhesConsulta from './DetalhesConsulta';
import TutorConsultas from './TutorConsultas';
import TutorHome from './TutorHome';
import TutorMeusPets from './TutorMeusPets';

export default function PainelTutor({ conta, aba, consultaId, navegar }) {
    const [dados, setDados] = useState(null);
    const [erro, setErro] = useState('');
    const [cancelando, setCancelando] = useState(false);
    const precisaPets = aba === 'inicio' || aba === 'meus-pets';
    const precisaConsultas = aba !== 'meus-pets' && aba !== 'agendar-consulta' && aba !== 'cadastrar-pet';

    useEffect(() => {
        if (!precisaPets && !precisaConsultas) return;
        let ativo = true;
        Promise.all([
            precisaPets ? listarMeusPets() : null,
            precisaConsultas ? listarMinhasConsultas() : null
        ]).then(([pets, consultas]) => {
            if (ativo) setDados({ pets: pets?.data || [], consultas: consultas?.data || [] });
        }).catch(() => { if (ativo) setErro('Não foi possível carregar os dados da sua conta.'); });
        return () => { ativo = false; };
    }, [precisaPets, precisaConsultas]);

    const consultas = () => navegar('consultas');
    if (aba === 'agendar-consulta') {
        return <AgendamentoForm onSucesso={(consulta) => navegar('sucesso-agendamento', consulta.id)}
            onCancelar={consultas} />;
    }
    if (aba === 'cadastrar-pet') {
        const pets = () => navegar('meus-pets');
        return <CadastrarPetIndividual onVoltar={pets} onSucesso={pets} />;
    }
    if (!dados) {
        return erro ? <p className="alert-box error" role="alert">{erro}</p> : <p role="status">Carregando…</p>;
    }
    if (aba === 'inicio') return <TutorHome conta={conta} {...dados} navegar={navegar} />;
    if (aba === 'meus-pets') return <TutorMeusPets pets={dados.pets}
        onCadastrarOutroPet={() => navegar('cadastrar-pet')} onAgendarConsulta={() => navegar('agendar-consulta')} />;
    if (aba === 'consultas') return <TutorConsultas consultas={dados.consultas}
        onAgendar={() => navegar('agendar-consulta')} onVerDetalhes={(id) => navegar('detalhes-consulta', id)} />;

    const consulta = dados.consultas.find((item) => String(item.id) === consultaId);
    if (!consulta) return <div className="form-card">
        <h1>Consulta indisponível</h1><Button onClick={consultas}>Ver minhas consultas</Button>
    </div>;
    if (aba === 'sucesso-agendamento') return <ConsultaAgendadaSucesso dados={consulta}
        onVerConsultas={consultas} onVoltarInicio={() => navegar('inicio')} />;
    if (aba === 'remarcar-consulta') return <AgendamentoForm consulta={consulta}
        onSucesso={(nova) => navegar('detalhes-consulta', nova.id)} onCancelar={consultas} />;

    const cancelar = async () => {
        setCancelando(true);
        setErro('');
        try {
            const { data } = await atualizarStatusConsulta(consulta.id, 'CANCELADA');
            setDados((atuais) => ({
                ...atuais, consultas: atuais.consultas.map((item) => item.id === data.id ? data : item)
            }));
        } catch (e) {
            setErro(e.response?.data?.mensagem || 'Não foi possível cancelar a consulta.');
        } finally {
            setCancelando(false);
        }
    };

    return <DetalhesConsulta consulta={consulta} onVoltar={consultas} erro={erro} cancelando={cancelando}
        onRemarcar={() => navegar('remarcar-consulta', consulta.id)} onCancelar={cancelar} />;
}
