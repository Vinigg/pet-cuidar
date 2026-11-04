import React, { useEffect, useState } from 'react';
import CadastroVeterinario from './CadastroVeterinario';
import { Button } from './common/UIComponents';
import { listarVeterinarios } from '../api/api';

export default function Veterinarios() {
  const [modo, setModo] = useState('cadastro');
  const [veterinarios, setVeterinarios] = useState([]);
  const [selecionado, setSelecionado] = useState('');
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState(null);
  const [tentativa, setTentativa] = useState(0);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (modo !== 'edicao') return;
    let cancelado = false;
    setCarregando(true);
    setErro(null);
    Promise.all([listarVeterinarios({ ativo: true }), listarVeterinarios({ ativo: false })])
      .then(([ativos, inativos]) => {
        if (cancelado) return;
        const profissionais = new Map([...ativos.data, ...inativos.data].map(item => [item.id, item]));
        setVeterinarios([...profissionais.values()]);
      })
      .catch(falha => {
        if (!cancelado) setErro(falha.response?.data?.mensagem || 'Não foi possível carregar os veterinários.');
      })
      .finally(() => { if (!cancelado) setCarregando(false); });
    return () => { cancelado = true; };
  }, [modo, tentativa]);

  const trocarModo = novoModo => {
    if (salvando || modo === novoModo) return;
    setSelecionado('');
    setVeterinarios([]);
    setErro(null);
    setModo(novoModo);
  };

  const atualizarOpcao = atualizado => {
    setVeterinarios(anteriores => anteriores.map(item => item.id === atualizado.id ? atualizado : item));
  };

  return (
    <div className="veterinario-management">
      <div className="form-container veterinario-toolbar">
        <div className="veterinario-mode" role="group" aria-label="Operação de veterinários">
          <Button type="button" variant={modo === 'cadastro' ? 'primary' : 'outlined'}
            aria-pressed={modo === 'cadastro'} disabled={salvando} onClick={() => trocarModo('cadastro')}>Cadastrar</Button>
          <Button type="button" variant={modo === 'edicao' ? 'primary' : 'outlined'}
            aria-pressed={modo === 'edicao'} disabled={salvando} onClick={() => trocarModo('edicao')}>Atualizar</Button>
        </div>

        {modo === 'edicao' && <section className="veterinario-selector" aria-label="Seleção de veterinário">
          {!selecionado && <h1 className="form-title">Atualizar Veterinário</h1>}
          {carregando ? <p role="status">Carregando veterinários...</p> : erro ? <>
            <div className="alert-box error" role="alert">{erro}</div>
            <Button type="button" variant="outlined" onClick={() => setTentativa(valor => valor + 1)}>Tentar novamente</Button>
          </> : veterinarios.length === 0 ? <p role="status">Nenhum veterinário cadastrado.</p> : <>
            <label className="input-label" htmlFor="veterinario-edicao">VETERINÁRIO</label>
            <select id="veterinario-edicao" className="select-custom" disabled={salvando}
              value={selecionado} onChange={evento => setSelecionado(evento.target.value)}>
              <option value="">Selecione um veterinário</option>
              {[...veterinarios].sort((primeiro, segundo) => primeiro.nome.localeCompare(segundo.nome, 'pt-BR'))
                .map(item => <option key={item.id} value={item.id}>
                  {item.nome}{item.especialidade ? ` - ${item.especialidade}` : ''}{item.ativo === false ? ' (Inativo)' : ''}
                </option>)}
            </select>
          </>}
        </section>}
      </div>

      {modo === 'cadastro' ? <CadastroVeterinario onSalvando={setSalvando} />
        : selecionado && !erro && !carregando && <CadastroVeterinario key={selecionado}
          veterinarioId={Number(selecionado)} onAtualizacao={atualizarOpcao} onSalvando={setSalvando} />}
    </div>
  );
}