import React, { useState } from "react";
import Navbar from "./components/Navbar";
import CadastroTutorPet from "./components/CadastroTutorPet";
import AgendamentoForm from "./components/AgendamentoForm";
import PainelRecepcionista from "./components/PainelRecepcionista";
import HistoricoPet from "./components/HistoricoPet";

export default function App() {
  const [abaAtiva, setAbaAtiva] = useState("cadastro");

  return (
    <div className="app">
      <Navbar abaAtiva={abaAtiva} onTrocarAba={setAbaAtiva} />
      <main className="conteudo">
        {abaAtiva === "cadastro" && <CadastroTutorPet />}
        {abaAtiva === "agendamento" && <AgendamentoForm />}
        {abaAtiva === "painel" && <PainelRecepcionista />}
        {abaAtiva === "historico" && <HistoricoPet />}
      </main>
    </div>
  );
}
