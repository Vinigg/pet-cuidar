import React, { useState, useEffect } from 'react';
import { Button, Badge } from './common/UIComponents';
import { 
  registrarAtendimento, 
  atualizarAtendimento, 
  obterAtendimento,
  listarAtendimentosPorPet, 
  listarPets, 
  listarVeterinarios,
  listarConsultasDoDia
} from '../api/api';
import { dataLocal } from '../utils/datas';

// A API devolve erros no formato {status, mensagem, timestamp}.
const mensagemDaApi = (err, padrao) =>
  err.response?.data?.mensagem || err.response?.data?.message || padrao;

const statusConsulta = {
  AGENDADA: 'Agendada',
  CONFIRMADA: 'Confirmada',
  REALIZADA: 'Realizada',
  CANCELADA: 'Cancelada',
  NAO_COMPARECEU: 'Não compareceu'
};

export default function FormularioAtendimento({ conta }) {
  // Estados para seleção
  const [pets, setPets] = useState([]);
  const [veterinarios, setVeterinarios] = useState([]);
  const [dataSelecionada, setDataSelecionada] = useState(dataLocal);
  const [consultas, setConsultas] = useState([]);
  const [carregandoConsultas, setCarregandoConsultas] = useState(true);
  const [erroAgenda, setErroAgenda] = useState('');
  const [revisaoAgenda, setRevisaoAgenda] = useState(0);
  const [atendimentosExistentes, setAtendimentosExistentes] = useState([]);

  // Estados do formulário
  const [modo, setModo] = useState('registrar'); // 'registrar' ou 'atualizar'
  const [petSelecionado, setPetSelecionado] = useState('');
  const [consultaSelecionada, setConsultaSelecionada] = useState('');
  const [veterinarioSelecionado, setVeterinarioSelecionado] = useState('');
  const [descricaoClinica, setDescricaoClinica] = useState('');
  const [atendimentoSelecionado, setAtendimentoSelecionado] = useState('');

  // Estados de UI
  const [loading, setLoading] = useState(false);
  const [carregandoOpcoes, setCarregandoOpcoes] = useState(true);
  const [mensagemSucesso, setMensagemSucesso] = useState(null);
  const [erro, setErro] = useState(null);
  const [errosValidacao, setErrosValidacao] = useState({});

  const veterinarioId = conta?.veterinarioId;
  const consultasDisponiveis = consultas.filter((consulta) => consulta.veterinarioId === veterinarioId);
  const consultaEscolhida = consultasDisponiveis.find((consulta) => String(consulta.id) === consultaSelecionada);

  // Carregar opções iniciais (pets e veterinários)
  useEffect(() => {
    const carregarOpcoes = async () => {
      try {
        setCarregandoOpcoes(true);
        const [resPets, resVets] = await Promise.all([
          listarPets(),
          listarVeterinarios()
        ]);
        setPets(Array.isArray(resPets.data) ? resPets.data : []);
        setVeterinarios(Array.isArray(resVets.data) ? resVets.data : []);
      } catch (err) {
        console.error('Erro ao carregar opções:', err);
        setErro('Erro ao carregar dados. Por favor, recarregue a página.');
      } finally {
        setCarregandoOpcoes(false);
      }
    };

    carregarOpcoes();
  }, []);

  useEffect(() => {
    if (!dataSelecionada) return;
    let ativo = true;

    listarConsultasDoDia(dataSelecionada).then(({ data }) => {
      if (ativo) setConsultas(Array.isArray(data) ? data : []);
    }).catch((err) => {
      if (ativo) setErroAgenda(mensagemDaApi(err, 'Não foi possível carregar as consultas. Tente novamente.'));
    }).finally(() => {
      if (ativo) setCarregandoConsultas(false);
    });

    return () => { ativo = false; };
  }, [dataSelecionada, revisaoAgenda]);

  const mudarData = (novaData) => {
    if (novaData > dataLocal() || novaData === dataSelecionada) return;

    setDataSelecionada(novaData);
    setConsultas([]);
    setCarregandoConsultas(Boolean(novaData));
    setErroAgenda('');
    setConsultaSelecionada('');
    setAtendimentoSelecionado('');
    setDescricaoClinica('');
    setErrosValidacao({});
    setErro(null);
    setMensagemSucesso(null);
  };

  // Carregar atendimentos quando pet é selecionado
  useEffect(() => {
    const carregarAtendimentos = async () => {
      if (!petSelecionado) {
        setAtendimentosExistentes([]);
        return;
      }

      try {
        const response = await listarAtendimentosPorPet(petSelecionado);
        setAtendimentosExistentes(Array.isArray(response.data) ? response.data : []);
      } catch (err) {
        console.error('Erro ao carregar atendimentos:', err);
        setAtendimentosExistentes([]);
      }
    };

    carregarAtendimentos();
  }, [petSelecionado]);

  // Carregar dados do atendimento ao selecionar para atualizar
  useEffect(() => {
    let ativo = true;
    const carregarAtendimento = async () => {
      if (!atendimentoSelecionado || modo !== 'atualizar') {
        setDescricaoClinica('');
        setConsultaSelecionada('');
        return;
      }

      try {
        const response = await obterAtendimento(atendimentoSelecionado);
        if (!ativo) return;
        const atendimento = response.data;
        setDescricaoClinica(atendimento.descricaoClinica || '');
        if (atendimento.consultaId) {
          setConsultaSelecionada(atendimento.consultaId);
        }
      } catch (err) {
        if (ativo) setErro('Erro ao carregar atendimento. Tente novamente.');
      }
    };

    carregarAtendimento();
    return () => { ativo = false; };
  }, [atendimentoSelecionado, modo]);

  // Validar formulário
  const validar = () => {
    const erros = {};

    if (modo === 'atualizar' && !petSelecionado) {
      erros.petSelecionado = 'Selecione um pet';
    }

    if (modo === 'atualizar' && !veterinarioSelecionado) {
      erros.veterinarioSelecionado = 'Selecione um veterinário';
    }

    if (modo === 'registrar' && !consultaEscolhida) {
      erros.consultaSelecionada = 'Selecione uma consulta';
    }

    if (!descricaoClinica.trim()) {
      erros.descricaoClinica = 'Descrição clínica é obrigatória';
    } else if (descricaoClinica.length < 10) {
      erros.descricaoClinica = 'Descrição deve ter pelo menos 10 caracteres';
    } else if (descricaoClinica.length > 2000) {
      erros.descricaoClinica = 'Descrição não pode exceder 2000 caracteres';
    }

    if (modo === 'atualizar' && !atendimentoSelecionado) {
      erros.atendimentoSelecionado = 'Selecione um atendimento para atualizar';
    }

    setErrosValidacao(erros);
    return Object.keys(erros).length === 0;
  };

  // Registrar novo atendimento
  const handleRegistrar = async (e) => {
    e.preventDefault();
    setErro(null);
    setMensagemSucesso(null);

    if (!validar()) return;

    setLoading(true);
    try {
      const dados = {
        petId: consultaEscolhida.petId,
        consultaId: consultaEscolhida.id,
        veterinarioId,
        descricaoClinica: descricaoClinica.trim()
      };

      await registrarAtendimento(dados);
      setMensagemSucesso('Atendimento registrado com sucesso!');
      
      // Limpar formulário
      setConsultaSelecionada('');
      setDescricaoClinica('');
      setErrosValidacao({});

      // A consulta passou para REALIZADA no backend: recarrega para mostrar o novo status.
      setConsultas([]);
      setErroAgenda('');
      setCarregandoConsultas(true);
      setRevisaoAgenda((valor) => valor + 1);

      // Ocultar mensagem após 4 segundos
      setTimeout(() => setMensagemSucesso(null), 4000);
    } catch (err) {
      // 409 (consulta cancelada/não compareceu) e 400 (consulta de outro pet) trazem a mensagem do backend.
      const mensagem = mensagemDaApi(err, 'Erro ao registrar atendimento. Tente novamente.');
      setErro(mensagem);
    } finally {
      setLoading(false);
    }
  };

  // Atualizar atendimento existente
  const handleAtualizar = async (e) => {
    e.preventDefault();
    setErro(null);
    setMensagemSucesso(null);

    if (!validar()) return;

    setLoading(true);
    try {
      const dados = {
        atendimentoId: parseInt(atendimentoSelecionado),
        veterinarioId: parseInt(veterinarioSelecionado),
        descricaoClinica: descricaoClinica.trim()
      };

      await atualizarAtendimento(dados);
      setMensagemSucesso('Atendimento atualizado com sucesso!');
      
      // Limpar formulário
      setDescricaoClinica('');
      setAtendimentoSelecionado('');
      setErrosValidacao({});

      // Recarregar atendimentos
      if (petSelecionado) {
        const response = await listarAtendimentosPorPet(petSelecionado);
        setAtendimentosExistentes(Array.isArray(response.data) ? response.data : []);
      }

      // Ocultar mensagem após 4 segundos
      setTimeout(() => setMensagemSucesso(null), 4000);
    } catch (err) {
      const mensagem = mensagemDaApi(err, 'Erro ao atualizar atendimento. Tente novamente.');
      setErro(mensagem);
    } finally {
      setLoading(false);
    }
  };

  if (carregandoOpcoes) {
    return (
      <div className="form-container">
        <div className="empty-state">Carregando formulário...</div>
      </div>
    );
  }

  return (
    <div className="form-container">
      <div className="form-card">
        {/* Cabeçalho */}
        <header className="form-header">
          <div>
            <Badge variant="primary">Prontuário Clínico</Badge>
            <h1 className="form-title" style={{ marginTop: '8px' }}>
              {modo === 'registrar' ? 'Registrar Atendimento' : 'Atualizar Atendimento'}
            </h1>
            <p className="form-subtitle">
              {modo === 'registrar' 
                ? 'Registre uma nova descrição clínica para um atendimento veterinário'
                : 'Atualize a descrição clínica de um atendimento existente'}
            </p>
          </div>
        </header>

        {/* Mensagens */}
        {mensagemSucesso && (
          <div className="alert alert-success">
            ✓ {mensagemSucesso}
          </div>
        )}

        {erro && (
          <div className="alert alert-error" role="alert">
            ✗ {erro}
          </div>
        )}

        {/* Seletor de Modo */}
        <div className="form-section">
          <div style={{ display: 'flex', gap: '12px', marginBottom: '24px' }}>
            <Button
              variant={modo === 'registrar' ? 'primary' : 'secondary'}
              onClick={() => {
                setModo('registrar');
                setAtendimentoSelecionado('');
                setDescricaoClinica('');
                setConsultaSelecionada('');
                setErrosValidacao({});
              }}
            >
              Novo Atendimento
            </Button>
            <Button
              variant={modo === 'atualizar' ? 'primary' : 'secondary'}
              onClick={() => {
                setModo('atualizar');
                setConsultaSelecionada('');
                setDescricaoClinica('');
                setErrosValidacao({});
              }}
            >
              Atualizar Existente
            </Button>
          </div>
        </div>

        <form onSubmit={modo === 'registrar' ? handleRegistrar : handleAtualizar}>
          <div className="form-group">
            <label className="input-label" htmlFor="data-consulta">Data da consulta</label>
            <input
              id="data-consulta"
              className="select-custom"
              type="date"
              value={dataSelecionada}
              max={dataLocal()}
              onChange={(e) => mudarData(e.target.value)}
              disabled={loading}
            />
          </div>
          {carregandoConsultas && <p role="status">Carregando consultas…</p>}
          {erroAgenda && <div className="alert alert-error" role="alert">{erroAgenda}</div>}
          {modo === 'atualizar' && <>
          {/* Seção 1: Seleção de Pet */}
          <div className="form-section">
            <h2 className="form-section-title">1. Paciente</h2>
            
            <div className="form-group">
              <label className="input-label" htmlFor="pet">
                Pet *
              </label>
              <select
                id="pet"
                className={`select-custom ${errosValidacao.petSelecionado ? 'error' : ''}`}
                value={petSelecionado}
                onChange={(e) => {
                  setPetSelecionado(e.target.value);
                  setAtendimentoSelecionado('');
                  setConsultaSelecionada('');
                  if (errosValidacao.petSelecionado) {
                    setErrosValidacao({ ...errosValidacao, petSelecionado: '' });
                  }
                }}
              >
                <option value="">Selecione um pet...</option>
                {pets.map(pet => (
                  <option key={pet.id} value={pet.id}>
                    {pet.nome} ({pet.especie}) - Tutor: {pet.tutorNome}
                  </option>
                ))}
              </select>
              {errosValidacao.petSelecionado && (
                <span className="input-error-msg">{errosValidacao.petSelecionado}</span>
              )}
            </div>
          </div>

          {/* Seção 2: Seleção de Veterinário */}
          <div className="form-section">
            <h2 className="form-section-title">2. Veterinário</h2>
            
            <div className="form-group">
              <label className="input-label" htmlFor="veterinario">
                Veterinário *
              </label>
              <select
                id="veterinario"
                className={`select-custom ${errosValidacao.veterinarioSelecionado ? 'error' : ''}`}
                value={veterinarioSelecionado}
                onChange={(e) => {
                  setVeterinarioSelecionado(e.target.value);
                  if (modo === 'registrar') {
                    setConsultaSelecionada('');
                  }
                  if (errosValidacao.veterinarioSelecionado) {
                    setErrosValidacao({ ...errosValidacao, veterinarioSelecionado: '' });
                  }
                }}
              >
                <option value="">Selecione um veterinário...</option>
                {veterinarios.map(vet => (
                  <option key={vet.id} value={vet.id}>
                    {vet.nome} {vet.especialidade ? `- ${vet.especialidade}` : ''}
                  </option>
                ))}
              </select>
              {errosValidacao.veterinarioSelecionado && (
                <span className="input-error-msg">{errosValidacao.veterinarioSelecionado}</span>
              )}
            </div>
          </div>

          </>}

          {/* Seleção de Consulta (apenas para registrar) */}
          {modo === 'registrar' && (
            <div className="form-section">
              <div className="form-group">
                <label className="input-label" htmlFor="consulta">
                  Consulta *
                </label>
                <select
                  id="consulta"
                  className={`select-custom ${errosValidacao.consultaSelecionada ? 'error' : ''}`}
                  value={consultaSelecionada}
                  disabled={!dataSelecionada || carregandoConsultas || Boolean(erroAgenda) || consultasDisponiveis.length === 0 || loading}
                  onChange={(e) => {
                    setConsultaSelecionada(e.target.value);
                    setDescricaoClinica('');
                    if (errosValidacao.consultaSelecionada) {
                      setErrosValidacao({ ...errosValidacao, consultaSelecionada: '' });
                    }
                  }}
                >
                  <option value="">
                    {!dataSelecionada && 'Selecione uma data primeiro'}
                    {carregandoConsultas && 'Aguarde o carregamento'}
                    {dataSelecionada && !carregandoConsultas && 'Selecione uma consulta...'}
                  </option>
                  {consultasDisponiveis.map(consulta => (
                    <option key={consulta.id} value={consulta.id}>
                      {consulta.dataHora.slice(11, 16)} · {consulta.petNome} · {statusConsulta[consulta.status] || consulta.status}
                    </option>
                  ))}
                </select>
                {dataSelecionada && !carregandoConsultas && !erroAgenda && consultasDisponiveis.length === 0 &&
                  <p className="empty-state">Nenhuma consulta na data selecionada.</p>}
                {errosValidacao.consultaSelecionada && (
                  <span className="input-error-msg">{errosValidacao.consultaSelecionada}</span>
                )}
              </div>
            </div>
          )}

          {/* Seção 4: Seleção de Atendimento (apenas para atualizar) */}
          {modo === 'atualizar' && petSelecionado && (
            <div className="form-section">
              <h2 className="form-section-title">3. Atendimento Existente</h2>
              
              {atendimentosExistentes.length === 0 ? (
                <div className="empty-state">
                  Nenhum atendimento encontrado para este pet.
                </div>
              ) : (
                <div className="form-group">
                  <label className="input-label" htmlFor="atendimento">
                    Selecione um atendimento para atualizar *
                  </label>
                  <select
                    id="atendimento"
                    className={`select-custom ${errosValidacao.atendimentoSelecionado ? 'error' : ''}`}
                    value={atendimentoSelecionado}
                    onChange={(e) => {
                      setAtendimentoSelecionado(e.target.value);
                      if (errosValidacao.atendimentoSelecionado) {
                        setErrosValidacao({ ...errosValidacao, atendimentoSelecionado: '' });
                      }
                    }}
                  >
                    <option value="">Selecione um atendimento...</option>
                    {atendimentosExistentes.map(atendimento => (
                      <option key={atendimento.id} value={atendimento.id}>
                        {new Date(atendimento.dataAtendimento).toLocaleString('pt-BR')} - 
                        {atendimento.descricaoClinica.substring(0, 50)}...
                      </option>
                    ))}
                  </select>
                  {errosValidacao.atendimentoSelecionado && (
                    <span className="input-error-msg">{errosValidacao.atendimentoSelecionado}</span>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Seção 5: Descrição Clínica */}
          <div className="form-section">
            <h2 className="form-section-title">
              {modo === 'registrar' ? 'Descrição Clínica' : 'Atualizar Descrição'}
            </h2>
            
            <div className="form-group">
              <label className="input-label" htmlFor="descricao">
                Descrição Clínica *
                <span style={{ fontSize: '0.85em', marginLeft: '8px', color: '#666' }}>
                  ({descricaoClinica.length}/2000)
                </span>
              </label>
              <textarea
                id="descricao"
                className={`textarea-custom ${errosValidacao.descricaoClinica ? 'error' : ''}`}
                value={descricaoClinica}
                onChange={(e) => {
                  setDescricaoClinica(e.target.value);
                  if (errosValidacao.descricaoClinica) {
                    setErrosValidacao({ ...errosValidacao, descricaoClinica: '' });
                  }
                }}
                placeholder="Digite os detalhes clínicos do atendimento, incluindo sintomas, diagnóstico, prescrições e observações importantes..."
                rows="8"
                maxLength="2000"
              />
              {errosValidacao.descricaoClinica && (
                <span className="input-error-msg">{errosValidacao.descricaoClinica}</span>
              )}
            </div>
          </div>

          {/* Botões de Ação */}
          <div className="form-actions">
            <Button
              variant="primary"
              type="submit"
              disabled={loading}
            >
              {loading ? 'Processando...' : 
               modo === 'registrar' ? '✓ Registrar Atendimento' : '✓ Atualizar Atendimento'}
            </Button>
            <Button
              variant="secondary"
              type="button"
              onClick={() => {
                setDescricaoClinica('');
                setConsultaSelecionada('');
                setAtendimentoSelecionado('');
                setErrosValidacao({});
                setErro(null);
              }}
              disabled={loading}
            >
              Limpar
            </Button>
          </div>
        </form>

        {/* Seção de Histórico de Atendimentos */}
        {petSelecionado && atendimentosExistentes.length > 0 && (
          <div className="form-section" style={{ marginTop: '32px', borderTop: '1px solid #e0e0e0', paddingTop: '24px' }}>
            <h2 className="form-section-title">Histórico de Atendimentos</h2>
            
            <div className="atendimentos-list">
              {atendimentosExistentes.map(atendimento => (
                <div key={atendimento.id} className="atendimento-card">
                  <div className="atendimento-header">
                    <span className="atendimento-data">
                      📅 {new Date(atendimento.dataAtendimento).toLocaleDateString('pt-BR')}
                    </span>
                    <span className="atendimento-vet">
                      👨‍⚕️ {atendimento.veterinarioNome}
                    </span>
                  </div>
                  <p className="atendimento-descricao">
                    {atendimento.descricaoClinica}
                  </p>
                  <div className="atendimento-footer">
                    <small className="atendimento-auditoria">
                      Criado: {new Date(atendimento.dataCriacao).toLocaleString('pt-BR')}
                    </small>
                    {new Date(atendimento.dataAtualizacao) > new Date(atendimento.dataCriacao) && (
                      <small className="atendimento-auditoria">
                        Atualizado: {new Date(atendimento.dataAtualizacao).toLocaleString('pt-BR')}
                      </small>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
