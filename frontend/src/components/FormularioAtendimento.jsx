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

// A API devolve erros no formato {status, mensagem, timestamp}.
const mensagemDaApi = (err, padrao) =>
  err.response?.data?.mensagem || err.response?.data?.message || padrao;

const dataDeHoje = () => {
  // Data local (toISOString usa UTC e pode virar o dia seguinte à noite)
  const agora = new Date();
  return `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-${String(agora.getDate()).padStart(2, '0')}`;
};

export default function FormularioAtendimento() {
  // Estados para seleção
  const [pets, setPets] = useState([]);
  const [veterinarios, setVeterinarios] = useState([]);
  const [consultas, setConsultas] = useState([]);
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

  const consultasDisponiveis = petSelecionado && veterinarioSelecionado
    ? consultas.filter(c =>
        String(c.petId) === String(petSelecionado) &&
        String(c.veterinarioId) === String(veterinarioSelecionado))
    : [];

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

  // Carregar consultas do dia
  const carregarConsultas = async () => {
    try {
      const response = await listarConsultasDoDia(dataDeHoje());
      setConsultas(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      console.error('Erro ao carregar consultas:', err);
    }
  };

  useEffect(() => {
    carregarConsultas();
  }, []);

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
    const carregarAtendimento = async () => {
      if (!atendimentoSelecionado || modo !== 'atualizar') {
        setDescricaoClinica('');
        setConsultaSelecionada('');
        return;
      }

      try {
        const response = await obterAtendimento(atendimentoSelecionado);
        const atendimento = response.data;
        setDescricaoClinica(atendimento.descricaoClinica || '');
        if (atendimento.consultaId) {
          setConsultaSelecionada(atendimento.consultaId);
        }
      } catch (err) {
        console.error('Erro ao carregar atendimento:', err);
        setErro('Erro ao carregar atendimento. Tente novamente.');
      }
    };

    carregarAtendimento();
  }, [atendimentoSelecionado, modo]);

  // Validar formulário
  const validar = () => {
    const erros = {};

    if (!petSelecionado) {
      erros.petSelecionado = 'Selecione um pet';
    }

    if (!veterinarioSelecionado) {
      erros.veterinarioSelecionado = 'Selecione um veterinário';
    }

    if (modo === 'registrar' && !consultaSelecionada) {
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
        petId: parseInt(petSelecionado),
        consultaId: parseInt(consultaSelecionada),
        veterinarioId: parseInt(veterinarioSelecionado),
        descricaoClinica: descricaoClinica.trim()
      };

      await registrarAtendimento(dados);
      setMensagemSucesso('Atendimento registrado com sucesso!');
      
      // Limpar formulário
      setConsultaSelecionada('');
      setDescricaoClinica('');
      setErrosValidacao({});

      // A consulta passou para REALIZADA no backend: recarrega para mostrar o novo status.
      await carregarConsultas();

      // Recarregar atendimentos
      if (petSelecionado) {
        const response = await listarAtendimentosPorPet(petSelecionado);
        setAtendimentosExistentes(Array.isArray(response.data) ? response.data : []);
      }

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

          {/* Seção 3: Seleção de Consulta (apenas para registrar) */}
          {modo === 'registrar' && (
            <div className="form-section">
              <h2 className="form-section-title">3. Consulta</h2>
              
              <div className="form-group">
                <label className="input-label" htmlFor="consulta">
                  Consulta *
                </label>
                <select
                  id="consulta"
                  className={`select-custom ${errosValidacao.consultaSelecionada ? 'error' : ''}`}
                  value={consultaSelecionada}
                  disabled={!petSelecionado || !veterinarioSelecionado}
                  onChange={(e) => {
                    setConsultaSelecionada(e.target.value);
                    if (errosValidacao.consultaSelecionada) {
                      setErrosValidacao({ ...errosValidacao, consultaSelecionada: '' });
                    }
                  }}
                >
                  <option value="">
                    {!petSelecionado || !veterinarioSelecionado
                      ? 'Selecione um pet e um veterinário primeiro'
                      : consultasDisponiveis.length === 0
                        ? 'Nenhuma consulta hoje para este pet e veterinário'
                        : 'Selecione uma consulta...'}
                  </option>
                  {consultasDisponiveis.map(consulta => (
                    <option key={consulta.id} value={consulta.id}>
                      {consulta.petNome} - {new Date(consulta.dataHora).toLocaleString('pt-BR')} ({consulta.status})
                    </option>
                  ))}
                </select>
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
              {modo === 'registrar' ? '4. Descrição Clínica' : '4. Atualizar Descrição'}
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
