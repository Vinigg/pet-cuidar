import React, { useState } from "react";
import { cadastrarTutor } from "../api/api";

const petVazio = { nome: "", especie: "", idade: "", alergiasCriticas: "" };

export default function CadastroTutorPet() {
  const [tutor, setTutor] = useState({ nome: "", email: "", telefone: "", cpf: "" });
  const [pets, setPets] = useState([{ ...petVazio }]);
  const [mensagem, setMensagem] = useState(null);
  const [erro, setErro] = useState(null);
  const [enviando, setEnviando] = useState(false);

  const atualizarPet = (index, campo, valor) => {
    const novosPets = [...pets];
    novosPets[index][campo] = valor;
    setPets(novosPets);
  };

  const adicionarPet = () => setPets([...pets, { ...petVazio }]);
  const removerPet = (index) => setPets(pets.filter((_, i) => i !== index));

  const enviar = async (e) => {
    e.preventDefault();
    setMensagem(null);
    setErro(null);
    setEnviando(true);
    try {
      const payload = {
        ...tutor,
        pets: pets.map((p) => ({ ...p, idade: p.idade ? Number(p.idade) : null })),
      };
      await cadastrarTutor(payload);
      setMensagem("Tutor e pet(s) cadastrados com sucesso!");
      setTutor({ nome: "", email: "", telefone: "", cpf: "" });
      setPets([{ ...petVazio }]);
    } catch (err) {
      setErro(err.response?.data?.mensagem || "Nao foi possivel cadastrar. Verifique os dados.");
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="card">
      <h2>Cadastro de tutor e pet</h2>
      <form onSubmit={enviar}>
        <fieldset>
          <legend>Dados do tutor</legend>
          <label>
            Nome
            <input required value={tutor.nome} onChange={(e) => setTutor({ ...tutor, nome: e.target.value })} />
          </label>
          <label>
            E-mail
            <input required type="email" value={tutor.email} onChange={(e) => setTutor({ ...tutor, email: e.target.value })} />
          </label>
          <label>
            Telefone
            <input value={tutor.telefone} onChange={(e) => setTutor({ ...tutor, telefone: e.target.value })} />
          </label>
          <label>
            CPF
            <input value={tutor.cpf} onChange={(e) => setTutor({ ...tutor, cpf: e.target.value })} />
          </label>
        </fieldset>

        {pets.map((pet, index) => (
          <fieldset key={index}>
            <legend>Pet {index + 1}</legend>
            <label>
              Nome do pet
              <input required value={pet.nome} onChange={(e) => atualizarPet(index, "nome", e.target.value)} />
            </label>
            <label>
              Especie
              <input value={pet.especie} onChange={(e) => atualizarPet(index, "especie", e.target.value)} />
            </label>
            <label>
              Idade
              <input type="number" min="0" value={pet.idade} onChange={(e) => atualizarPet(index, "idade", e.target.value)} />
            </label>
            <label>
              Alergias / reacoes criticas
              <input
                placeholder="ex.: alergia a dipirona"
                value={pet.alergiasCriticas}
                onChange={(e) => atualizarPet(index, "alergiasCriticas", e.target.value)}
              />
            </label>
            {pets.length > 1 && (
              <button type="button" className="btn-secundario" onClick={() => removerPet(index)}>
                Remover pet
              </button>
            )}
          </fieldset>
        ))}

        <button type="button" className="btn-secundario" onClick={adicionarPet}>
          + Adicionar outro pet
        </button>

        <div className="acoes">
          <button type="submit" disabled={enviando}>
            {enviando ? "Enviando..." : "Cadastrar"}
          </button>
        </div>

        {mensagem && <p className="sucesso">{mensagem}</p>}
        {erro && <p className="erro">{erro}</p>}
      </form>
    </div>
  );
}
