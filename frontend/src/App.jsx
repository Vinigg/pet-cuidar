import React, { useCallback, useEffect, useState } from 'react';
import { api, consultarSessao, sair } from './api/api';
import Login from './components/Login';
import CriarContaTutor from './components/CriarContaTutor';
import TutorNavbar from './components/tutor/TutorNavbar';
import PainelTutor from './components/tutor/PainelTutor';
import Navbar from './components/Navbar';
import PainelRecepcionista from './components/PainelRecepcionista';
import CadastroTutorPet from './components/CadastroTutorPet';
import AgendamentoForm from './components/AgendamentoForm';
import HistoricoPet from './components/HistoricoPet';
import FormularioAtendimento from './components/FormularioAtendimento';
import Veterinarios from './components/Veterinarios';
import MinhaAgenda from './components/MinhaAgenda';
import Equipe from './components/Equipe';
import { Button } from './components/common/UIComponents';

const paineis = {
    TUTOR: { prefixo: 'tutor', inicio: 'inicio' },
    VETERINARIO: {
        prefixo: 'veterinario', nome: 'Veterinário', inicio: 'historico',
        abas: [
            { id: 'agenda', label: 'Minha agenda', Tela: MinhaAgenda },
            { id: 'historico', label: 'Histórico do pet', Tela: HistoricoPet },
            { id: 'atendimentos', label: 'Registrar atendimento', Tela: FormularioAtendimento }
        ]
    },
    COLABORADOR: {
        prefixo: 'colaborador', nome: 'Colaborador', inicio: 'painel',
        abas: [
            { id: 'painel', label: 'Painel do dia', Tela: PainelRecepcionista },
            { id: 'agendamento', label: 'Agendar consulta', Tela: AgendamentoForm },
            { id: 'cadastro', label: 'Cadastro', Tela: CadastroTutorPet }
        ]
    },
    ADMIN: {
        prefixo: 'admin', nome: 'Admin', inicio: 'veterinarios',
        abas: [
            { id: 'veterinarios', label: 'Veterinários', Tela: Veterinarios },
            { id: 'colaboradores', label: 'Contas da equipe', Tela: Equipe }
        ]
    }
};

const rotasTutor = new Set(['inicio', 'meus-pets', 'cadastrar-pet', 'consultas',
    'agendar-consulta', 'detalhes-consulta', 'remarcar-consulta', 'sucesso-agendamento']);
const rotasComConsulta = new Set(['detalhes-consulta', 'remarcar-consulta', 'sucesso-agendamento']);
const lerRota = () => window.location.hash.slice(1) || '/';

export default function App() {
    const [rota, setRota] = useState(lerRota);
    const [conta, setConta] = useState(null);
    const [carregando, setCarregando] = useState(true);
    const [erroSessao, setErroSessao] = useState('');
    const [emailVerificado, setEmailVerificado] = useState('');
    const [saindo, setSaindo] = useState(false);

    const navegar = useCallback((destino, substituir = false) => {
        if (substituir) {
            window.history.replaceState(null, '', '#' + destino);
        } else {
            window.location.hash = destino;
        }
        setRota(destino);
    }, []);

    useEffect(() => {
        const atualizarRota = () => setRota(lerRota());
        window.addEventListener('hashchange', atualizarRota);
        const interceptor = api.interceptors.response.use(undefined, (erro) => {
            if (erro.response?.status === 401 && !erro.config?.url?.startsWith('/auth/')) {
                setConta(null);
                setEmailVerificado('');
                navegar('/login', true);
            }
            return Promise.reject(erro);
        });
        let ativo = true;
        consultarSessao().then(({ data }) => {
            if (ativo) setConta(data);
        }).catch((erro) => {
            if (ativo && erro.response?.status !== 401) {
                setErroSessao('Não foi possível consultar a sessão.');
            }
        }).finally(() => {
            if (ativo) setCarregando(false);
        });
        return () => {
            ativo = false;
            window.removeEventListener('hashchange', atualizarRota);
            api.interceptors.response.eject(interceptor);
        };
    }, [navegar]);

    const painel = conta && paineis[conta.perfil];
    const inicio = painel && `/${painel.prefixo}/${painel.inicio}`;
    const publica = rota === '/' || rota === '/login' || rota === '/criar-conta';

    useEffect(() => {
        if (carregando) return;
        if (painel && (publica || rota === `/${painel.prefixo}`)) navegar(inicio, true);
        if (!painel && rota !== '/login' && rota !== '/criar-conta') navegar('/login', true);
    }, [carregando, painel, publica, rota, inicio, navegar]);

    const entrar = (usuario) => {
        setErroSessao('');
        setEmailVerificado('');
        setConta(usuario);
        const destino = paineis[usuario.perfil];
        if (destino) navegar(`/${destino.prefixo}/${destino.inicio}`, true);
    };

    const sairDoApp = async () => {
        setSaindo(true);
        setErroSessao('');
        try {
            await sair();
            setConta(null);
            navegar('/login', true);
        } catch {
            setErroSessao('Não foi possível encerrar a sessão. Tente novamente.');
        } finally {
            setSaindo(false);
        }
    };

    if (carregando) {
        return <main className="form-container" role="status">Verificando sessão…</main>;
    }

    if (!painel) {
        const voltar = () => {
            setEmailVerificado('');
            navegar('/login');
        };
        if (rota === '/criar-conta' && emailVerificado) {
            return <CriarContaTutor email={emailVerificado} onContaCriada={entrar} onVoltarLogin={voltar} />;
        }
        return rota === '/criar-conta'
            ? <Login key="cadastro" finalidade="CADASTRO" onVerificado={(_, email) => setEmailVerificado(email)}
                onAlternar={voltar} erroSessao={erroSessao} />
            : <Login key="login" finalidade="LOGIN" onVerificado={entrar}
                onAlternar={() => navegar('/criar-conta')} erroSessao={erroSessao} />;
    }

    const [, prefixo, aba, id, ...restante] = (publica || rota === `/${painel.prefixo}` ? inicio : rota).split('/');
    const irPara = (destino, consultaId) => navegar(
        `/${painel.prefixo}/${destino}${consultaId ? '/' + consultaId : ''}`);
    const tela = painel.abas?.find((item) => item.id === aba);
    const tutor = conta.perfil === 'TUTOR';
    const permitida = prefixo === painel.prefixo && restante.length === 0 && (tutor
        ? rotasTutor.has(aba) && (rotasComConsulta.has(aba) ? /^[1-9][0-9]*$/.test(id || '') : id === undefined)
        : tela && id === undefined);
    const Tela = tela?.Tela;

    return (
        <div className="app-container">
            {tutor
                ? <TutorNavbar abaTutor={permitida ? aba : ''} setAbaTutor={irPara} usuario={conta}
                    onLogout={sairDoApp} saindo={saindo} />
                : <Navbar abaAtiva={permitida ? aba : ''} setAbaAtiva={irPara} usuario={conta}
                    abas={painel.abas} perfil={painel.nome} onLogout={sairDoApp} saindo={saindo} />}
            <main className="main-content" key={`${conta.id}:${rota}`}>
                {erroSessao && <div className="alert-box error" role="alert">{erroSessao}</div>}
                {!permitida ? (
                    <section className="form-card">
                        <h1>Acesso negado</h1>
                        <Button onClick={() => navegar(inicio)}>Voltar ao meu painel</Button>
                    </section>
                ) : tutor ? <PainelTutor conta={conta} aba={aba} consultaId={id} navegar={irPara} />
                    : <Tela onNovoAgendamento={() => irPara('agendamento')}
                        onRegistrarAtendimento={() => irPara('atendimentos')} />}
            </main>
        </div>
    );
}
