import React, { useEffect, useState } from 'react';
import { Badge, Button } from './common/UIComponents';
import {
  atualizarAtendimento,
  listarAtendimentosPorPet,
  listarConsultasDoDia,
  registrarAtendimento
} from '../api/api';
import { dataLocal } from '../utils/datas';

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
  const [dataSelecionada, setDataSelecionada] = useState(dataLocal);
  const [consultas, setConsultas] = useState([]);
  const [carregandoConsultas, setCarregandoConsultas] = useState(true);
  const [erroAgenda, setErroAgenda] = useState('');
  const [revisaoAgenda, setRevisaoAgenda] = useState(0);

  const [modo, setModo] = useState('registrar');
  const [consultaSelecionada, setConsultaSelecionada] = useState('');
  const [resultadoAtendimentos, setResultadoAtendimentos] = useState(null);
  const [revisaoAtendimentos, setRevisaoAtendimentos] = useState(0);
  const [atendimentoSelecionado, setAtendimentoSelecionado] = useState('');
  const [descricaoClinica, setDescricaoClinica] = useState('');
  const [erroDescricao, setErroDescricao] = useState('');

  const [salvando, setSalvando] = useState(false);
  const [mensagemSucesso, setMensagemSucesso] = useState('');
  const [erro, setErro] = useState('');

  const veterinarioId = conta?.veterinarioId;
  const consultasDisponiveis = consultas.filter((consulta) => consulta.veterinarioId === veterinarioId);
  const consultaEscolhida = consultasDisponiveis.find((consulta) => String(consulta.id) === consultaSelecionada);
  const consultaId = consultaEscolhida?.id;
  const petId = consultaEscolhida?.petId;
  const prontuario = resultadoAtendimentos?.consultaId === consultaId ? resultadoAtendimentos : null;
  const atendimentos = prontuario?.registros ?? [];
  const atendimentoEscolhido = atendimentos.find((atendimento) => String(atendimento.id) === atendimentoSelecionado);
  const registrando = modo === 'registrar';
  const podeEditar = Boolean(consultaEscolhida && (registrando || atendimentoEscolhido));
  const podeSalvar = podeEditar && !salvando && !carregandoConsultas;

  useEffect(() => {
    if (!dataSelecionada) return;
    let ativo = true;

    listarConsultasDoDia(dataSelecionada).then(({ data }) => {
      if (ativo) setConsultas(Array.isArray(data) ? data : []);
    }).catch((err) => {
      if (!ativo) return;
      setConsultas([]);
      setErroAgenda(mensagemDaApi(err, 'Não foi possível carregar as consultas. Tente novamente.'));
    }).finally(() => {
      if (ativo) setCarregandoConsultas(false);
    });

    return () => { ativo = false; };
  }, [dataSelecionada, revisaoAgenda]);

  useEffect(() => {
    if (!consultaId) return;
    let ativo = true;

    listarAtendimentosPorPet(petId).then(({ data }) => {
      if (!ativo) return;
      const registros = Array.isArray(data) ? data.filter((registro) => registro.consultaId === consultaId) : [];
      setResultadoAtendimentos({ consultaId, registros });
    }).catch((err) => {
      if (ativo) setResultadoAtendimentos({
        consultaId,
        erro: mensagemDaApi(err, 'Não foi possível carregar os atendimentos desta consulta.')
      });
    });

    return () => { ativo = false; };
  }, [consultaId, petId, revisaoAtendimentos]);

  useEffect(() => {
    if (!mensagemSucesso) return;
    const temporizador = setTimeout(() => setMensagemSucesso(''), 4000);
    return () => clearTimeout(temporizador);
  }, [mensagemSucesso]);

  const limparDescricao = () => {
    setAtendimentoSelecionado('');
    setDescricaoClinica('');
    setErroDescricao('');
  };

  const mudarConsulta = (id) => {
    if (id !== consultaSelecionada) setResultadoAtendimentos(null);
    setConsultaSelecionada(id);
    limparDescricao();
    setErro('');
    setMensagemSucesso('');
  };

  const mudarData = (novaData) => {
    if (novaData > dataLocal() || novaData === dataSelecionada) return;
    mudarConsulta('');
    setDataSelecionada(novaData);
    setConsultas([]);
    setCarregandoConsultas(Boolean(novaData));
    setErroAgenda('');
  };

  const mudarModo = (novoModo) => {
    if (novoModo === modo) return;
    setModo(novoModo);
    limparDescricao();
    setErro('');
    setMensagemSucesso('');
  };

  const mudarAtendimento = (id) => {
    const escolhido = atendimentos.find((atendimento) => String(atendimento.id) === id);
    setAtendimentoSelecionado(id);
    setDescricaoClinica(escolhido?.descricaoClinica ?? '');
    setErroDescricao('');
    setErro('');
  };

  const salvar = async (evento) => {
    evento.preventDefault();
    if (!podeSalvar) return;

    const descricao = descricaoClinica.trim();
    if (descricao.length < 10) {
      setErroDescricao(descricao ? 'Descrição deve ter pelo menos 10 caracteres' : 'Descrição clínica é obrigatória');
      return;
    }
    if (descricao.length > 2000) {
      setErroDescricao('Descrição não pode exceder 2000 caracteres');
      return;
    }

    setErro('');
    setMensagemSucesso('');
    setSalvando(true);
    try {
      if (registrando) {
        await registrarAtendimento({ petId, consultaId, veterinarioId, descricaoClinica: descricao });
        setMensagemSucesso('Atendimento registrado com sucesso!');
        setErroAgenda('');
        setCarregandoConsultas(true);
        setRevisaoAgenda((valor) => valor + 1);
      } else {
        await atualizarAtendimento({
          atendimentoId: atendimentoEscolhido.id,
          veterinarioId,
          descricaoClinica: descricao
        });
        setMensagemSucesso('Atendimento atualizado com sucesso!');
      }
      limparDescricao();
      setResultadoAtendimentos(null);
      setRevisaoAtendimentos((valor) => valor + 1);
    } catch (err) {
      setErro(mensagemDaApi(err, registrando
        ? 'Erro ao registrar atendimento. Tente novamente.'
        : 'Erro ao atualizar atendimento. Tente novamente.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <div className="form-container">
      <div className="form-card">
        <header className="form-header">
          <div>
            <Badge variant="primary">Prontuário Clínico</Badge>
            <h1 className="form-title" style={{ marginTop: '8px' }}>
              {registrando ? 'Registrar Atendimento' : 'Atualizar Atendimento'}
            </h1>
          </div>
        </header>

        {mensagemSucesso && <div className="alert alert-success" role="status">{mensagemSucesso}</div>}
        {erro && <div className="alert alert-error" role="alert">{erro}</div>}

        <div className="form-section">
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <Button variant={registrando ? 'primary' : 'secondary'} aria-pressed={registrando}
              type="button" disabled={salvando} onClick={() => mudarModo('registrar')}>
              Novo Atendimento
            </Button>
            <Button variant={registrando ? 'secondary' : 'primary'} aria-pressed={!registrando}
              type="button" disabled={salvando} onClick={() => mudarModo('atualizar')}>
              Atualizar Existente
            </Button>
          </div>
        </div>

        <form onSubmit={salvar}>
          <div className="form-section">
            <div className="form-group">
              <label className="input-label" htmlFor="data-consulta">Data da consulta</label>
              <input id="data-consulta" className="select-custom" type="date"
                value={dataSelecionada} max={dataLocal()} disabled={salvando}
                onChange={(e) => mudarData(e.target.value)} />
            </div>

            <div className="form-group">
              <label className="input-label" htmlFor="consulta">Consulta</label>
              <select id="consulta" className="select-custom" value={consultaSelecionada}
                disabled={salvando || carregandoConsultas || consultasDisponiveis.length === 0}
                onChange={(e) => mudarConsulta(e.target.value)}>
                <option value="">{dataSelecionada ? 'Selecione uma consulta' : 'Selecione uma data primeiro'}</option>
                {consultasDisponiveis.map((consulta) => (
                  <option key={consulta.id} value={consulta.id}>
                    {consulta.dataHora.slice(11, 16)} · {consulta.petNome} · {statusConsulta[consulta.status] || consulta.status}
                  </option>
                ))}
              </select>
              {carregandoConsultas && <p role="status">Carregando consultas…</p>}
              {erroAgenda && <div className="alert alert-error" role="alert">{erroAgenda}</div>}
              {dataSelecionada && !carregandoConsultas && !erroAgenda && consultasDisponiveis.length === 0 &&
                <p className="empty-state">Nenhuma consulta na data selecionada.</p>}
            </div>
          </div>

          {consultaEscolhida && (
            <div className="form-section">
              {!prontuario && <p role="status">Carregando atendimentos…</p>}
              {prontuario?.erro && <div className="alert alert-error" role="alert">{prontuario.erro}</div>}
              {!registrando && prontuario?.registros && (atendimentos.length === 0
                ? <p className="empty-state">Esta consulta ainda não tem atendimento registrado.</p>
                : (
                  <div className="form-group">
                    <label className="input-label" htmlFor="atendimento">Atendimento para atualizar</label>
                    <select id="atendimento" className="select-custom" value={atendimentoSelecionado}
                      disabled={salvando} onChange={(e) => mudarAtendimento(e.target.value)}>
                      <option value="">Selecione um atendimento</option>
                      {atendimentos.map((atendimento) => (
                        <option key={atendimento.id} value={atendimento.id}>
                          {new Date(atendimento.dataCriacao).toLocaleString('pt-BR')} · {(atendimento.descricaoClinica ?? '').slice(0, 70)}
                        </option>
                      ))}
                    </select>
                  </div>
                ))}
            </div>
          )}

          <div className="form-section">
            <div className="form-group">
              <label className="input-label" htmlFor="descricao">
                Descrição Clínica
                <span style={{ fontSize: '0.85em', marginLeft: '8px' }}>({descricaoClinica.length}/2000)</span>
              </label>
              <textarea id="descricao" className={erroDescricao ? 'textarea-custom error' : 'textarea-custom'}
                value={descricaoClinica} rows="8" maxLength="2000" disabled={salvando || !podeEditar}
                onChange={(e) => {
                  setDescricaoClinica(e.target.value);
                  setErroDescricao('');
                }} />
              {erroDescricao && <span className="input-error-msg">{erroDescricao}</span>}
            </div>
          </div>

          <div className="form-actions">
            <Button type="submit" disabled={!podeSalvar}>
              {salvando ? 'Processando...' : registrando ? 'Registrar Atendimento' : 'Atualizar Atendimento'}
            </Button>
            <Button variant="secondary" type="button" disabled={salvando} onClick={() => mudarConsulta('')}>
              Limpar
            </Button>
          </div>
        </form>

        {atendimentos.length > 0 && (
          <div className="form-section" style={{ marginTop: '32px', borderTop: '1px solid #e0e0e0', paddingTop: '24px' }}>
            <h2 className="form-section-title">Registros da consulta</h2>
            <div className="atendimentos-list">
              {atendimentos.map((atendimento) => (
                <div key={atendimento.id} className="atendimento-card">
                  <div className="atendimento-header">
                    <span className="atendimento-data">
                      {atendimento.dataAtendimento && new Date(atendimento.dataAtendimento).toLocaleString('pt-BR')}
                    </span>
                    <span className="atendimento-vet">{atendimento.veterinarioNome}</span>
                  </div>
                  <p className="atendimento-descricao">{atendimento.descricaoClinica}</p>
                  <div className="atendimento-footer">
                    <small className="atendimento-auditoria">
                      Criado em {new Date(atendimento.dataCriacao).toLocaleString('pt-BR')}
                    </small>
                    {atendimento.dataAtualizacao > atendimento.dataCriacao && (
                      <small className="atendimento-auditoria">
                        Atualizado em {new Date(atendimento.dataAtualizacao).toLocaleString('pt-BR')}
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
