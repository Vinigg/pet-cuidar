import React, { useEffect, useRef, useState } from 'react';
import { Button, Input } from './common/UIComponents';
import { cadastrarVeterinario, obterVeterinario, listarHorariosVeterinario,
  atualizarVeterinario, atualizarHorariosVeterinario } from '../api/api';

const DIAS = [
  { valor: 'MONDAY', nome: 'Segunda-feira' },
  { valor: 'TUESDAY', nome: 'Terça-feira' },
  { valor: 'WEDNESDAY', nome: 'Quarta-feira' },
  { valor: 'THURSDAY', nome: 'Quinta-feira' },
  { valor: 'FRIDAY', nome: 'Sexta-feira' },
  { valor: 'SATURDAY', nome: 'Sábado' },
  { valor: 'SUNDAY', nome: 'Domingo' },
];

const escalaVazia = () => Object.fromEntries(DIAS.map(dia => [dia.valor, []]));
const naGrade = valor => /^(?:[01]\d|2[0-3]):(?:00|30)$/.test(valor);

export default function CadastroVeterinario({ veterinarioId, onAtualizacao, onSalvando }) {
  const edicao = veterinarioId != null;
  const [nome, setNome] = useState('');
  const [especialidade, setEspecialidade] = useState('');
  const [ativo, setAtivo] = useState(true);
  const [escala, setEscala] = useState(escalaVazia);
  const [erros, setErros] = useState({});
  const [erro, setErro] = useState(null);
  const [mensagem, setMensagem] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [salvandoSecao, setSalvandoSecao] = useState(null);
  const [carregando, setCarregando] = useState(edicao);
  const [erroCarga, setErroCarga] = useState(null);
  const [tentativa, setTentativa] = useState(0);
  const proximoPeriodo = useRef(0);
  const envioEmAndamento = useRef(false);

  useEffect(() => {
    if (veterinarioId == null) return;
    let cancelado = false;
    setCarregando(true);
    setErroCarga(null);
    setErro(null);
    setMensagem(null);
    setErros({});
    Promise.all([obterVeterinario(veterinarioId), listarHorariosVeterinario(veterinarioId)])
      .then(([cadastro, horarios]) => {
        if (cancelado) return;
        const novaEscala = escalaVazia();
        horarios.data.forEach(periodo => {
          novaEscala[periodo.diaSemana].push({ id: proximoPeriodo.current++,
            horaInicio: periodo.horaInicio.slice(0, 5), horaFim: periodo.horaFim.slice(0, 5) });
        });
        setNome(cadastro.data.nome);
        setEspecialidade(cadastro.data.especialidade || '');
        setAtivo(cadastro.data.ativo);
        setEscala(novaEscala);
      })
      .catch(falha => {
        if (!cancelado) setErroCarga(falha.response?.data?.mensagem || 'Não foi possível carregar o veterinário.');
      })
      .finally(() => { if (!cancelado) setCarregando(false); });
    return () => { cancelado = true; };
  }, [veterinarioId, tentativa]);

  const novoPeriodo = (inicio = '08:00', fim = '12:00') => ({
    id: proximoPeriodo.current++, horaInicio: inicio, horaFim: fim,
  });

  const alterarEscala = (dia, periodos) => {
    setEscala(anterior => ({ ...anterior, [dia]: periodos }));
    setErros({});
    setMensagem(null);
  };

  const alterarPeriodo = (dia, id, campo, valor) => {
    alterarEscala(dia, escala[dia].map(periodo => periodo.id === id ? { ...periodo, [campo]: valor } : periodo));
  };

  const validar = (secao = 'cadastro') => {
    const falhas = {};
    if (secao !== 'horarios') {
      if (!nome.trim()) falhas.nome = 'Informe o nome do veterinário.';
      if (nome.trim().length > 255) falhas.nome = 'Use no máximo 255 caracteres.';
      if (especialidade.trim().length > 255) falhas.especialidade = 'Use no máximo 255 caracteres.';
    }
    if (secao === 'dados') return falhas;
    const total = Object.values(escala).reduce((soma, periodos) => soma + periodos.length, 0);
    if (!total && !edicao) falhas.escala = 'Selecione pelo menos um dia de atendimento.';
    if (total > 100) falhas.escala = 'Cadastre no máximo 100 períodos.';
    DIAS.forEach(dia => {
      const ordenados = [...escala[dia.valor]].sort((primeiro, segundo) => primeiro.horaInicio.localeCompare(segundo.horaInicio));
      ordenados.forEach((periodo, indice) => {
        if (!naGrade(periodo.horaInicio) || !naGrade(periodo.horaFim)) {
          falhas[periodo.id] = 'Use horários em intervalos de 30 minutos.';
        } else if (periodo.horaInicio >= periodo.horaFim) {
          falhas[periodo.id] = 'O fim deve ser depois do início, no mesmo dia.';
        } else if (indice > 0 && periodo.horaInicio < ordenados[indice - 1].horaFim) {
          falhas[periodo.id] = 'Este período se sobrepõe a outro no mesmo dia.';
        }
      });
    });
    return falhas;
  };

  const periodosParaEnvio = () => DIAS.flatMap(dia => escala[dia.valor].map(({ horaInicio, horaFim }) => ({
    diaSemana: dia.valor, horaInicio, horaFim,
  })));

  const salvarEdicao = async (evento, secao) => {
    evento.preventDefault();
    if (envioEmAndamento.current) return;
    setErro(null);
    setMensagem(null);
    const falhas = validar(secao);
    setErros(falhas);
    if (Object.keys(falhas).length) return;
    envioEmAndamento.current = true;
    setEnviando(true);
    setSalvandoSecao(secao);
    if (onSalvando) onSalvando(true);
    try {
      if (secao === 'dados') {
        const resposta = await atualizarVeterinario(veterinarioId, {
          nome: nome.trim(), especialidade: especialidade.trim(), ativo,
        });
        setMensagem('Dados do veterinário atualizados com sucesso!');
        if (onAtualizacao) onAtualizacao(resposta.data);
      } else {
        await atualizarHorariosVeterinario(veterinarioId, { horarios: periodosParaEnvio() });
        setMensagem('Horários de atendimento atualizados com sucesso!');
      }
    } catch (falha) {
      setErro(falha.response?.data?.mensagem || 'Não foi possível atualizar o veterinário. Tente novamente.');
    } finally {
      envioEmAndamento.current = false;
      setEnviando(false);
      setSalvandoSecao(null);
      if (onSalvando) onSalvando(false);
    }
  };

  const handleSubmit = async evento => {
    evento.preventDefault();
    if (envioEmAndamento.current) return;
    setErro(null);
    setMensagem(null);
    const falhas = validar();
    setErros(falhas);
    if (Object.keys(falhas).length) return;
    const dados = {
      nome: nome.trim(),
      especialidade: especialidade.trim(),
      horarios: periodosParaEnvio(),
    };
    envioEmAndamento.current = true;
    setEnviando(true);
    if (onSalvando) onSalvando(true);
    try {
      await cadastrarVeterinario(dados);
      setMensagem(`Veterinário ${dados.nome} cadastrado com sucesso!`);
      setNome('');
      setEspecialidade('');
      setEscala(escalaVazia());
      setErros({});
    } catch (falha) {
      setErro(falha.response?.data?.mensagem || 'Não foi possível cadastrar o veterinário. Tente novamente.');
    } finally {
      envioEmAndamento.current = false;
      setEnviando(false);
      if (onSalvando) onSalvando(false);
    }
  };

  const camposDados = (
            <section className="form-section" aria-labelledby="veterinario-dados">
              <div className="form-section-header">
                <span className="form-step-badge">1</span>
                <h2 className="form-section-title" id="veterinario-dados">Dados do profissional</h2>
              </div>
              <div className="form-grid-2">
                <Input label="NOME COMPLETO" aria-label="Nome do veterinário" name="nome"
                  placeholder="Ex: Ana Souza" value={nome} maxLength={255} required
                  onChange={evento => { setNome(evento.target.value); setErros({}); setMensagem(null); }}
                  error={erros.nome} aria-invalid={Boolean(erros.nome)} />
                <Input label="ESPECIALIDADE" aria-label="Especialidade" name="especialidade"
                  placeholder="Ex: Clínica geral" value={especialidade} maxLength={255}
                  onChange={evento => { setEspecialidade(evento.target.value); setErros({}); setMensagem(null); }}
                  error={erros.especialidade} aria-invalid={Boolean(erros.especialidade)} />
              </div>
              {edicao && <label className="veterinario-day-label">
                <input type="checkbox" checked={ativo}
                  onChange={evento => { setAtivo(evento.target.checked); setMensagem(null); }} />
                <span>Profissional ativo</span>
              </label>}
            </section>
  );

  const camposHorarios = (
            <section className="form-section" aria-labelledby="veterinario-escala">
              <div className="form-section-header">
                <span className="form-step-badge">2</span>
                <h2 className="form-section-title" id="veterinario-escala">Horários de atendimento</h2>
                <span className="veterinario-duration">Consultas de 30 min</span>
              </div>
              {erros.escala && <div className="input-error-msg" role="alert">{erros.escala}</div>}
              <div className="veterinario-week">
                {DIAS.map(dia => (
                  <div className={`veterinario-day ${escala[dia.valor].length ? 'selected' : ''}`} key={dia.valor}>
                    <label className="veterinario-day-label">
                      <input type="checkbox" checked={escala[dia.valor].length > 0}
                        onChange={evento => alterarEscala(dia.valor, evento.target.checked ? [novoPeriodo()] : [])} />
                      <span>{dia.nome}</span>
                    </label>
                    <div className="veterinario-day-periods">
                      {escala[dia.valor].length === 0 ? <span className="veterinario-day-off">Sem atendimento</span> : (
                        <>
                          {escala[dia.valor].map((periodo, indice) => (
                            <div className="veterinario-period" key={periodo.id}>
                              <div className="veterinario-period-fields">
                                <Input label="INÍCIO" aria-label={`${dia.nome}, início do período ${indice + 1}`}
                                  type="time" step="1800" value={periodo.horaInicio} required
                                  aria-invalid={Boolean(erros[periodo.id])} aria-describedby={erros[periodo.id] ? `periodo-erro-${periodo.id}` : undefined}
                                  onChange={evento => alterarPeriodo(dia.valor, periodo.id, 'horaInicio', evento.target.value)} />
                                <Input label="FIM" aria-label={`${dia.nome}, fim do período ${indice + 1}`}
                                  type="time" step="1800" value={periodo.horaFim} required
                                  aria-invalid={Boolean(erros[periodo.id])} aria-describedby={erros[periodo.id] ? `periodo-erro-${periodo.id}` : undefined}
                                  onChange={evento => alterarPeriodo(dia.valor, periodo.id, 'horaFim', evento.target.value)} />
                                <button type="button" className="veterinario-remove-period"
                                  title={`Remover período ${indice + 1} de ${dia.nome}`}
                                  aria-label={`Remover período ${indice + 1} de ${dia.nome}`}
                                  onClick={() => alterarEscala(dia.valor, escala[dia.valor].filter(item => item.id !== periodo.id))}>
                                  <span aria-hidden="true">&times;</span>
                                </button>
                              </div>
                              {erros[periodo.id] && <span className="input-error-msg" id={`periodo-erro-${periodo.id}`} role="alert">{erros[periodo.id]}</span>}
                            </div>
                          ))}
                          <Button type="button" variant="outlined" size="sm" className="veterinario-add-period"
                            aria-label={`Adicionar período em ${dia.nome}`}
                            onClick={() => alterarEscala(dia.valor, [...escala[dia.valor], novoPeriodo('14:00', '18:00')])}>
                            <span aria-hidden="true">+</span> Adicionar período
                          </Button>
                        </>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </section>
  );

  if (carregando) return <div className="form-container veterinario-page" role="status">Carregando veterinário...</div>;
  if (erroCarga) return <div className="form-container veterinario-page">
    <div className="alert-box error" role="alert">{erroCarga}</div>
    <Button type="button" variant="outlined" onClick={() => setTentativa(valor => valor + 1)}>Tentar novamente</Button>
  </div>;

  return (
    <div className="form-container veterinario-page">
      <div className="form-card">
        <header className="form-header">
          <h1 className="form-title">{edicao ? 'Atualizar Veterinário' : 'Cadastro de Veterinários'}</h1>
        </header>

        {mensagem && <div className="alert-box success" role="status">{mensagem}</div>}
        {erro && <div className="alert-box error" role="alert">{erro}</div>}

        {edicao ? <>
          <form onSubmit={evento => salvarEdicao(evento, 'dados')} className="form-body" aria-label="Atualizar dados">
            <fieldset disabled={enviando} className="veterinario-fields" aria-busy={enviando}>
              {camposDados}
              <div className="form-actions-footer">
                <Button type="submit" disabled={enviando}>{salvandoSecao === 'dados' ? 'Salvando...' : 'Salvar dados'}</Button>
              </div>
            </fieldset>
          </form>
          <hr className="form-divider" />
          <form onSubmit={evento => salvarEdicao(evento, 'horarios')} className="form-body" aria-label="Atualizar horários">
            <fieldset disabled={enviando} className="veterinario-fields" aria-busy={enviando}>
              {camposHorarios}
              <div className="form-actions-footer">
                <Button type="submit" disabled={enviando}>{salvandoSecao === 'horarios' ? 'Salvando...' : 'Salvar horários'}</Button>
              </div>
            </fieldset>
          </form>
        </> : <form onSubmit={handleSubmit} className="form-body">
          <fieldset disabled={enviando} className="veterinario-fields" aria-busy={enviando}>
            {camposDados}
            <hr className="form-divider" />
            {camposHorarios}
            <div className="form-actions-footer">
              <Button variant="primary" size="lg" type="submit" disabled={enviando}>
                {enviando ? 'Cadastrando...' : 'Cadastrar veterinário'}
              </Button>
            </div>
          </fieldset>
        </form>}
      </div>
    </div>
  );
}