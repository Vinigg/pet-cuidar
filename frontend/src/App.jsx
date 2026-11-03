import React, { useState } from 'react';
import Navbar from './components/Navbar';
import CadastroTutorPet from './components/CadastroTutorPet';
import AgendamentoForm from './components/AgendamentoForm';
import PainelRecepcionista from './components/PainelRecepcionista';
import HistoricoPet from './components/HistoricoPet';

export default function App() {
    const [abaAtiva, setAbaAtiva] = useState('cadastro');

    return (
        <div className="app-container">
            <Navbar abaAtiva={abaAtiva} setAbaAtiva={setAbaAtiva} />

            <main className="main-content">
                {abaAtiva === 'cadastro' && <CadastroTutorPet />}
                {abaAtiva === 'agendamento' && <AgendamentoForm />}
                {abaAtiva === 'painel' && <PainelRecepcionista />}
                {abaAtiva === 'historico' && <HistoricoPet />}
            </main>
        </div>
    );
}