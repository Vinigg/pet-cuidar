import React, { useState } from 'react';
import Login from './components/Login';
import CriarContaTutor from './components/CriarContaTutor';

// Módulos do Tutor
import TutorNavbar from './components/tutor/TutorNavbar';
import TutorHome from './components/tutor/TutorHome';
import TutorMeusPets from './components/tutor/TutorMeusPets';
import CadastrarPetIndividual from './components/tutor/CadastrarPetIndividual';
import AgendamentoTutor from './components/tutor/AgendamentoTutor';
import ConsultaAgendadaSucesso from './components/tutor/ConsultaAgendadaSucesso';
import TutorConsultas from './components/tutor/TutorConsultas';
import DetalhesConsulta from './components/tutor/DetalhesConsulta';

// Módulos da Recepção / Clínica
import Navbar from './components/Navbar';
import PainelRecepcionista from './components/PainelRecepcionista';
import CadastroTutorPet from './components/CadastroTutorPet';
import AgendamentoForm from './components/AgendamentoForm';
import HistoricoPet from './components/HistoricoPet';

export default function App() {
    const [usuarioLogado, setUsuarioLogado] = useState(null);
    const [telaAtual, setTelaAtual] = useState('login');

    const [abaTutor, setAbaTutor] = useState('inicio');
    const [dadosAgendamentoSucesso, setDadosAgendamentoSucesso] = useState(null);
    const [abaClinica, setAbaClinica] = useState('painel');

    // FLUXO DESLOGADO
    if (!usuarioLogado) {
        if (telaAtual === 'criar-conta') {
            return (
                <CriarContaTutor
                    onContaCriada={(novo) => {
                        setUsuarioLogado(novo);
                        setAbaTutor('inicio');
                    }}
                    onVoltarLogin={() => setTelaAtual('login')}
                />
            );
        }

        return (
            <Login
                onLogin={(dados) => {
                    setUsuarioLogado(dados);
                    if (dados.role === 'tutor') {
                        setAbaTutor('inicio');
                    }
                }}
                onCadastrarTutor={() => setTelaAtual('criar-conta')}
            />
        );
    }

    // FLUXO DO TUTOR
    if (usuarioLogado.role === 'tutor') {
        return (
            <div className="app-container">
                <TutorNavbar
                    abaTutor={abaTutor}
                    setAbaTutor={setAbaTutor}
                    usuario={usuarioLogado}
                    onLogout={() => {
                        setUsuarioLogado(null);
                        setTelaAtual('login');
                    }}
                />
                <main className="main-content">
                    {abaTutor === 'inicio' && (
                        <TutorHome onNavegar={(destino) => setAbaTutor(destino)} />
                    )}
                    {abaTutor === 'meus-pets' && (
                        <TutorMeusPets
                            onCadastrarOutroPet={() => setAbaTutor('cadastrar-pet')}
                            onAgendarConsulta={() => setAbaTutor('agendar-consulta')}
                        />
                    )}
                    {abaTutor === 'cadastrar-pet' && (
                        <CadastrarPetIndividual
                            onVoltar={() => setAbaTutor('meus-pets')}
                            onSucesso={() => setAbaTutor('meus-pets')}
                        />
                    )}
                    {abaTutor === 'consultas' && (
                        <TutorConsultas
                            onAgendar={() => setAbaTutor('agendar-consulta')}
                            onVerDetalhes={() => setAbaTutor('detalhes-consulta')}
                        />
                    )}
                    {abaTutor === 'agendar-consulta' && (
                        <AgendamentoTutor
                            onAvancarSucesso={(dados) => {
                                setDadosAgendamentoSucesso(dados);
                                setAbaTutor('sucesso-agendamento');
                            }}
                            onCancelar={() => setAbaTutor('inicio')}
                        />
                    )}
                    {abaTutor === 'sucesso-agendamento' && (
                        <ConsultaAgendadaSucesso
                            dados={dadosAgendamentoSucesso}
                            onVerConsultas={() => setAbaTutor('consultas')}
                            onVoltarInicio={() => setAbaTutor('inicio')}
                        />
                    )}
                    {abaTutor === 'detalhes-consulta' && (
                        <DetalhesConsulta
                            onVoltar={() => setAbaTutor('consultas')}
                            onRemarcar={() => setAbaTutor('agendar-consulta')}
                            onCancelar={() => setAbaTutor('consultas')}
                        />
                    )}
                </main>
            </div>
        );
    }

    // FLUXO DA CLÍNICA
    return (
        <div className="app-container">
            <Navbar
                aba={abaClinica}
                setAba={setAbaClinica}
                usuario={usuarioLogado}
                onLogout={() => {
                    setUsuarioLogado(null);
                    setTelaAtual('login');
                }}
            />
            <main className="main-content">
                {abaClinica === 'painel' && <PainelRecepcionista />}
                {abaClinica === 'cadastro' && <CadastroTutorPet />}
                {abaClinica === 'agendamento' && <AgendamentoForm />}
                {abaClinica === 'historico' && <HistoricoPet />}
            </main>
        </div>
    );
}