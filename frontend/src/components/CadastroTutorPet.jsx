import React, { useState } from "react";
import { cadastrarTutor } from "../api/api";
import { formatarCpf, formatarTelefone, somenteDigitos, validarCadastro } from "../utils/validacaoCadastro";

const petVazio = { nome: "", especie: "", idade: "", alergiasCriticas: "" };

export default function CadastroTutorPet() {
  const [tutor, setTutor] = useState({ nome: "", email: "", telefone: "", cpf: "" });
  const [pets, setPets] = useState([{ ...petVazio }]);
  const [mensagem, setMensagem] = useState(null);
  const [erro, setErro] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [erros, setErros] = useState({});

  const limparErro = (campo) => setErros((atuais) => ({ ...atuais, [campo]: undefined }));
  const validarCampo = (campo) => setErros((atuais) => ({
    ...atuais, [campo]: validarCadastro(tutor, pets)[campo],
  }));
  const atributosCampo = (campo) => ({
    "aria-invalid": Boolean(erros[campo]),
    "aria-describedby": erros[campo] ? `erro-${campo}` : undefined,
    onBlur: () => validarCampo(campo),
  });
  const erroCampo = (campo) => erros[campo] && (
    <span id={`erro-${campo}`} className="erro-campo" role="alert">{erros[campo]}</span>
  );
  const atualizarTutor = (campo, valor) => {
    setTutor({ ...tutor, [campo]: valor });
    limparErro(campo);
  };

  const atualizarPet = (index, campo, valor) => {
    setPets(pets.map((pet, indice) => indice === index ? { ...pet, [campo]: valor } : pet));
    limparErro(`pets.${index}.${campo}`);
  };

  const adicionarPet = () => setPets([...pets, { ...petVazio }]);
  const removerPet = (index) => {
    setPets(pets.filter((_, indice) => indice !== index));
    setErros((atuais) => Object.fromEntries(Object.entries(atuais).filter(([campo]) => !campo.startsWith("pets."))));
  };

  const enviar = async (e) => {
    e.preventDefault();
    if (enviando) return;
    setMensagem(null);
    setErro(null);
    const novosErros = validarCadastro(tutor, pets);
    setErros(novosErros);
    if (Object.keys(novosErros).length) return;
    setEnviando(true);
    try {
      const payload = {
        ...tutor,
        nome: tutor.nome.trim(),
        email: tutor.email.trim(),
        cpf: somenteDigitos(tutor.cpf),
        telefone: somenteDigitos(tutor.telefone),
        pets: pets.map((pet) => ({
          ...pet,
          nome: pet.nome.trim(),
          especie: pet.especie.trim(),
          alergiasCriticas: pet.alergiasCriticas.trim(),
          idade: pet.idade !== "" ? Number(pet.idade) : null,
        })),
      };
      await cadastrarTutor(payload);
      setMensagem("Tutor e pet(s) cadastrados com sucesso!");
      setTutor({ nome: "", email: "", telefone: "", cpf: "" });
      setPets([{ ...petVazio }]);
      setErros({});
    } catch (err) {
      setErro(err.response?.data?.mensagem || "Nao foi possivel cadastrar. Verifique os dados.");
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="card">
      <h2>Cadastro de tutor e pet</h2>
      <form onSubmit={enviar} noValidate>
        <fieldset disabled={enviando}>
          <legend>Dados do tutor</legend>
          <label>
            Nome
            <input required autoComplete="name" {...atributosCampo("nome")} value={tutor.nome} onChange={(e) => atualizarTutor("nome", e.target.value)} />
            {erroCampo("nome")}
          </label>
          <label>
            E-mail
            <input required type="email" autoComplete="email" {...atributosCampo("email")} value={tutor.email} onChange={(e) => atualizarTutor("email", e.target.value)} />
            {erroCampo("email")}
          </label>
          <label>
            Telefone
            <input type="tel" autoComplete="tel-national" inputMode="tel" placeholder="(11) 99999-9999" maxLength={15} {...atributosCampo("telefone")} value={tutor.telefone} onChange={(e) => atualizarTutor("telefone", formatarTelefone(e.target.value))} />
            {erroCampo("telefone")}
          </label>
          <label>
            CPF
            <input inputMode="numeric" placeholder="000.000.000-00" maxLength={14} {...atributosCampo("cpf")} value={tutor.cpf} onChange={(e) => atualizarTutor("cpf", formatarCpf(e.target.value))} />
            {erroCampo("cpf")}
          </label>
        </fieldset>

        {pets.map((pet, index) => (
          <fieldset key={index} disabled={enviando}>
            <legend>Pet {index + 1}</legend>
            <label>
              Nome do pet
              <input required {...atributosCampo(`pets.${index}.nome`)} value={pet.nome} onChange={(e) => atualizarPet(index, "nome", e.target.value)} />
              {erroCampo(`pets.${index}.nome`)}
            </label>
            <label>
              Especie
              <input value={pet.especie} onChange={(e) => atualizarPet(index, "especie", e.target.value)} />
            </label>
            <label>
              Idade
              <input type="number" min="0" step="1" {...atributosCampo(`pets.${index}.idade`)} value={pet.idade} onChange={(e) => atualizarPet(index, "idade", e.target.value)} />
              {erroCampo(`pets.${index}.idade`)}
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

        <button type="button" className="btn-secundario" onClick={adicionarPet} disabled={enviando}>
          + Adicionar outro pet
        </button>

        <div className="acoes">
          <button type="submit" disabled={enviando}>
            {enviando ? "Enviando..." : "Cadastrar"}
          </button>
        </div>

        {mensagem && <p className="sucesso" role="status">{mensagem}</p>}
        {erro && <p className="erro" role="alert">{erro}</p>}
      </form>
    </div>
  );
}
