import React, { useState } from 'react';
import { Button, Input } from './common/UIComponents';
import { cadastrarTutor } from '../api/api';
import { formatarCpf, formatarTelefone, somenteDigitos, validarCadastro } from '../utils/validacaoCadastro';

export default function CadastroTutorPet({ onCadastroSucesso }) {
  const [tutor, setTutor] = useState({ nome: '', email: '', telefone: '', cpf: '' });
  const [pets, setPets] = useState([
    { nome: '', especie: '', idade: '', alergiasCriticas: '' }
  ]);
  const [loading, setLoading] = useState(false);
  const [mensagem, setMensagem] = useState(null);
  const [erro, setErro] = useState(null);
  const [erros, setErros] = useState({});

  const handlePetChange = (index, campo, valor) => {
    const novosPets = [...pets];
    novosPets[index][campo] = valor;
    setPets(novosPets);
    setErros({});
  };

  const handleTutorChange = (campo, valor) => {
    setTutor({ ...tutor, [campo]: valor });
    setErros({});
  };

  const adicionarPet = () => {
    setPets([...pets, { nome: '', especie: '', idade: '', alergiasCriticas: '' }]);
  };

  const removerPet = (index) => {
    if (pets.length > 1) {
      setPets(pets.filter((_, i) => i !== index));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errosCadastro = validarCadastro(tutor, pets);
    setErros(errosCadastro);
    if (Object.keys(errosCadastro).length > 0) return;

    setLoading(true);
    setMensagem(null);
    setErro(null);

    try {
      await cadastrarTutor({
        nome: tutor.nome.trim(),
        email: tutor.email.trim(),
        cpf: somenteDigitos(tutor.cpf),
        telefone: somenteDigitos(tutor.telefone),
        pets: pets.filter((pet) => pet.nome.trim()).map((pet) => ({
          ...pet,
          nome: pet.nome.trim(),
          especie: pet.especie.trim(),
          idade: pet.idade === '' ? null : Number(pet.idade),
          alergiasCriticas: pet.alergiasCriticas.trim(),
        })),
      });

      setMensagem('Tutor e Pet(s) cadastrados com sucesso!');
      setTutor({ nome: '', email: '', telefone: '', cpf: '' });
      setPets([{ nome: '', especie: '', idade: '', alergiasCriticas: '' }]);
      setErros({});

      if (onCadastroSucesso) onCadastroSucesso();
    } catch (err) {
      setErro(err.response?.data?.mensagem || 'Erro ao realizar o cadastro.');
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="form-container">
        <div className="form-card">
          <header className="form-header">
            <h1 className="form-title">Cadastro de Tutor e Pet</h1>
            <p className="form-subtitle">
              Preencha os dados do tutor responsável e os animais de estimação para atendimento clínico.
            </p>
          </header>

          {mensagem && <div className="alert-box success">{mensagem}</div>}
          {erro && <div className="alert-box error">{erro}</div>}

          <form onSubmit={handleSubmit} className="form-body">
            {/* Sessão 1: Tutor */}
            <section className="form-section">
              <div className="form-section-header">
                <span className="form-step-badge">1</span>
                <h2 className="form-section-title">Dados do Tutor</h2>
              </div>

              <div className="form-grid-2">
                <Input
                    label="NOME COMPLETO"
                    placeholder="Ex: Carlos Silva"
                    value={tutor.nome}
                    onChange={(e) => handleTutorChange('nome', e.target.value)}
                    error={erros.nome}
                    required
                />
                <Input
                    label="E-MAIL"
                    type="email"
                    placeholder="Ex: carlos@email.com"
                    value={tutor.email}
                    onChange={(e) => handleTutorChange('email', e.target.value)}
                    error={erros.email}
                    aria-invalid={Boolean(erros.email)}
                    required
                />
              </div>

              <div className="form-grid-2">
                <Input
                    label="TELEFONE / WHATSAPP"
                    type="tel"
                    placeholder="(81) 99999-9999"
                    value={tutor.telefone}
                    onChange={(e) => handleTutorChange('telefone', formatarTelefone(e.target.value))}
                    error={erros.telefone}
                />
                <Input
                    label="CPF"
                    placeholder="000.000.000-00"
                    value={tutor.cpf}
                    onChange={(e) => handleTutorChange('cpf', formatarCpf(e.target.value))}
                    error={erros.cpf}
                    aria-invalid={Boolean(erros.cpf)}
                />
              </div>
            </section>

            <hr className="form-divider" />

            {/* Sessão 2: Pets */}
            <section className="form-section">
              <div className="form-section-header">
                <span className="form-step-badge">2</span>
                <h2 className="form-section-title">Dados do(s) Pet(s)</h2>
              </div>

              {pets.map((p, index) => (
                  <div key={index} className="sub-card-pet">
                    <div className="sub-card-pet-header">
                      <span className="pet-count-label">Pet {index + 1}</span>
                      {pets.length > 1 && (
                          <button
                              type="button"
                              onClick={() => removerPet(index)}
                              className="btn-remove-pet"
                          >
                            Remover
                          </button>
                      )}
                    </div>

                    <div className="form-grid-2">
                      <Input
                          label="NOME DO PET"
                          placeholder="Ex: Thor"
                          value={p.nome}
                          onChange={(e) => handlePetChange(index, 'nome', e.target.value)}
                          error={erros[`pets.${index}.nome`]}
                          required
                      />
                      <Input
                          label="ESPÉCIE / RAÇA"
                          placeholder="Ex: Canina (Labrador)"
                          value={p.especie}
                          onChange={(e) => handlePetChange(index, 'especie', e.target.value)}
                          required
                      />
                    </div>

                    <div className="form-grid-2">
                      <Input
                          label="IDADE (ANOS)"
                          type="number"
                          placeholder="Ex: 4"
                          value={p.idade}
                          onChange={(e) => handlePetChange(index, 'idade', e.target.value)}
                          error={erros[`pets.${index}.idade`]}
                      />
                      <Input
                          label="ALERGIAS CRÍTICAS OU OBSERVAÇÕES"
                          placeholder="Ex: Alergia a Dipirona"
                          value={p.alergiasCriticas}
                          onChange={(e) => handlePetChange(index, 'alergiasCriticas', e.target.value)}
                      />
                    </div>
                  </div>
              ))}

              <div className="add-pet-action">
                <Button type="button" variant="outlined" size="sm" onClick={adicionarPet}>
                  + Adicionar Outro Pet
                </Button>
              </div>
            </section>

            <div className="form-actions-footer">
              <Button variant="primary" size="lg" type="submit" disabled={loading}>
                {loading ? 'Cadastrando...' : 'Concluir Cadastro'}
              </Button>
            </div>
          </form>
        </div>
      </div>
  );
}