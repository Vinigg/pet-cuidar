import React from "react";

const ABAS = [
  { id: "cadastro", label: "Cadastro" },
  { id: "agendamento", label: "Agendar consulta" },
  { id: "painel", label: "Painel do dia" },
  { id: "historico", label: "Historico do pet" },
];

export default function Navbar({ abaAtiva, onTrocarAba }) {
  return (
    <nav className="navbar">
      <div className="navbar-titulo">🐾 Pet Cuidar</div>
      <div className="navbar-abas">
        {ABAS.map((aba) => (
          <button
            key={aba.id}
            className={`navbar-aba ${abaAtiva === aba.id ? "ativa" : ""}`}
            onClick={() => onTrocarAba(aba.id)}
          >
            {aba.label}
          </button>
        ))}
      </div>
    </nav>
  );
}
