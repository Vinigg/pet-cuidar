import React, { useEffect, useRef, useState } from 'react';
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
    const mutacao = useRef(false);
    const precisaPets = ['inicio', 'meus-pets', 'detalhes-consulta'].includes(aba);
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
    const cancelar = async (id = consultaId) => {
        if (mutacao.current) return false;
        mutacao.current = true;
        setCancelando(true);
        setErro('');
        try {
            const { data } = await atualizarStatusConsulta(id, 'CANCELADA');
            setDados(atuais => ({
                ...atuais, consultas: atuais.consultas.map(item => item.id === data.id ? data : item)
            }));
            return true;
        } catch (e) {
            setErro(e.response?.data?.mensagem || 'Não foi possível cancelar a consulta.');
            return false;
        } finally {
            mutacao.current = false;
            setCancelando(false);
        }
    };
    if (aba === 'agendar-consulta') {
        return <AgendamentoForm onSucesso={(consulta) => navegar('sucesso-agendamento', consulta.id)}
            visualTutor onCancelar={consultas} />;
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
        cancelando={cancelando} erro={erro} onCancelar={cancelar}
        onRemarcar={id => navegar('remarcar-consulta', id)}
        onAgendar={() => navegar('agendar-consulta')} onVerDetalhes={(id) => navegar('detalhes-consulta', id)} />;

    const consulta = dados.consultas.find((item) => String(item.id) === consultaId);
    if (!consulta) return <div className="form-card">
        <h1>Consulta indisponível</h1><Button onClick={consultas}>Ver minhas consultas</Button>
    </div>;
    if (aba === 'sucesso-agendamento') return <ConsultaAgendadaSucesso dados={consulta}
        onVerConsultas={consultas} onVoltarInicio={() => navegar('inicio')} />;
    if (aba === 'remarcar-consulta') return <AgendamentoForm consulta={consulta}
        visualTutor onSucesso={(nova) => navegar('detalhes-consulta', nova.id)} onCancelar={consultas} />;

    return <DetalhesConsulta consulta={consulta} pet={dados.pets.find(p => p.id === consulta.petId)} onVoltar={consultas} erro={erro} cancelando={cancelando}
        onRemarcar={() => navegar('remarcar-consulta', consulta.id)} onCancelar={cancelar} />;
}
