import React, { useState } from 'react';
import { Badge, Button } from './common/UIComponents';

const PRONTUARIOS_MAP = {
  1: {
    petId: 1,
    petNome: 'Thor',
    especie: 'Cachorro',
    raca: 'Golden Retriever',
    idade: '4 anos',
    peso: '32,4 kg',
    microchip: '981.098.223.109',
    prontuarioCod: '#TC-8842',
    tutorNome: 'Mariana Costa',
    tutorTel: '(11) 98765-4321',
    tutorEmail: 'mariana.costa@email.com',
    alergiasCriticas: 'DIPIRONA E PENICILINA (Reação anafilactoide prévia documentada em Outubro/2022. Não administrar nem prescrever anti-inflamatórios ou derivados de penicilina sem supervisão médica expressa).',
    resumoHistorico: 'Paciente canino com histórico recorrente de dermatite atópica sob controle terapêutico com banhos e Apoquel contínuo. Otite externa bilateral em 09/2026 tratada com sucesso com Otomax. Protocolo vacinal V10, Raiva e Giárdia atualizado.',
    historico: [
      {
        id: 1,
        data: '2026-10-22T14:30:00',
        vetNome: 'Dra. Ana Souza (CRMV-SP 42.189)',
        tipo: 'Retorno clínico & Dermatologia',
        status: 'Realizada',
        descricao: 'Avaliação da melhora do prurido cutâneo em patas anteriores. Lesões eritematosas em nítida regressão com xampu de clorexidina. Solicitada citologia de controle enviada ao laboratório de apoio.'
      },
      {
        id: 2,
        data: '2026-09-15T14:15:00',
        vetNome: 'Dra. Ana Souza (CRMV-SP 42.189)',
        tipo: 'Consulta de Urgência - Otite',
        status: 'Realizada',
        descricao: 'Paciente apresentava prurido auricular intenso e secreção ceruminosa acastanhada bilateral. Diagnóstico clínico de otite externa bacteriana/fúngica. Prescrição de suspensão ótica por 10 dias com orientações de limpeza auricular preventiva.'
      },
      {
        id: 3,
        data: '2026-06-10T10:00:00',
        vetNome: 'Dr. Paulo Castro (CRMV-SP 31.840)',
        tipo: 'Check-up Preventivo Semestral',
        status: 'Realizada',
        descricao: 'Exame clínico de rotina, ausculta cardiopulmonar normal (FC 88 bpm, FR 22 mpm), palpação abdominal indolor sem alterações de órgãos. Peso estável: 31,8 kg. Laudo de hemograma e perfil bioquímico dentro dos padrões de normalidade.'
      },
      {
        id: 4,
        data: '2025-08-15T16:30:00',
        vetNome: 'Dra. Camila Duarte (CRMV-SP 51.002)',
        tipo: 'Vacinação Anual & Triagem Alérgica',
        status: 'Realizada',
        descricao: 'Aplicação da vacina déctupla V10 e antirrábica. Paciente sem intercorrências no pós-vacinal imediato. Reforço de alerta no prontuário para alergia medicamentosa a dipirona e orientações pós-imunização passadas à tutora Mariana.'
      }
    ]
  },
  2: {
    petId: 2,
    petNome: 'Luna',
    especie: 'Gato',
    raca: 'Siamês Tradicional',
    idade: '2 anos',
    peso: '4,1 kg',
    microchip: '981.042.871.405',
    prontuarioCod: '#LN-4412',
    tutorNome: 'Mariana Costa',
    tutorTel: '(11) 98765-4321',
    tutorEmail: 'mariana.costa@email.com',
    alergiasCriticas: null,
    resumoHistorico: 'Felina saudável, castrada, sem histórico de alergias medicamentosas. Vermifugação e vacinação V4 em dia.',
    historico: [
      {
        id: 101,
        data: '2026-08-10T11:00:00',
        vetNome: 'Dr. Vinícius Lima (CRMV-SP 39.810)',
        tipo: 'Avaliação Oftalmológica & Check-up',
        status: 'Realizada',
        descricao: 'Exame oftalmológico com lâmpada de fenda sem alterações. Córneas claras, reflexo pupilar normal. Peso ideal de 4,1 kg mantido com ração super premium.'
      }
    ]
  },
  3: {
    petId: 3,
    petNome: 'Simba',
    especie: 'Gato',
    raca: 'Persa Tradicional',
    idade: '3 anos',
    peso: '4,8 kg',
    microchip: '981.042.871.405',
    prontuarioCod: '#SB-1090',
    tutorNome: 'Juliana Faria',
    tutorTel: '(11) 98765-4321',
    tutorEmail: 'juliana.faria@email.com',
    alergiasCriticas: 'SENSIBILIDADE OCULAR CRÔNICA (Rinite alérgica sazonal leve, sem contraindicação a fármacos comuns).',
    resumoHistorico: 'Paciente felino com acompanhamento oftalmológico e respiratório. Histórico de faltas a consultas de retorno.',
    historico: [
      {
        id: 201,
        data: '2026-07-22T09:30:00',
        vetNome: 'Dra. Camila Duarte (CRMV-SP 51.002)',
        tipo: 'Ultrassom Abdominal de Rotina',
        status: 'Realizada',
        descricao: 'Órgãos abdominais sem alterações. Rins com boa relação cortiçomedular. Bexiga com paredes lisas.'
      }
    ]
  }
};

export default function HistoricoPet() {
  const [petSelecionadoId, setPetSelecionadoId] = useState(1);
  const [modoFallback, setModoFallback] = useState(false);
  const [tipoAtendimento, setTipoAtendimento] = useState('Consulta de retorno');
  const [evolucaoTexto, setEvolucaoTexto] = useState('');
  const [alertaSalvo, setAlertaSalvo] = useState(null);

  // Busca o prontuário correspondente do mapa
  const prontuario = PRONTUARIOS_MAP[petSelecionadoId] || PRONTUARIOS_MAP[1];

  const handleSalvarAtendimento = (e) => {
    e.preventDefault();
    if (!evolucaoTexto.trim()) {
      alert('Por favor, descreva a evolução clínica do atendimento antes de salvar.');
      return;
    }

    const novoRegistro = {
      id: Date.now(),
      data: new Date().toISOString(),
      vetNome: 'Dra. Ana Souza (CRMV-SP 42.189)',
      tipo: tipoAtendimento,
      status: 'Realizada',
      descricao: evolucaoTexto.trim()
    };

    prontuario.historico.unshift(novoRegistro);
    setEvolucaoTexto('');
    setAlertaSalvo('✓ Atendimento registrado com sucesso no prontuário!');
    setTimeout(() => setAlertaSalvo(null), 3500);
  };

  return (
      <div className="clinica-container">
        {alertaSalvo && (
            <div className="alert-box success" style={{ marginBottom: '16px' }}>
              {alertaSalvo}
            </div>
        )}

        <header className="history-header" style={{ marginBottom: '20px' }}>
          <div>
          <span style={{ fontSize: '0.72rem', fontWeight: 800, color: 'var(--color-primary)' }}>
            PRONTUÁRIO CLÍNICO UNIFICADO
          </span>
            <h1 className="form-title" style={{ marginTop: '4px' }}>Histórico do Paciente</h1>
            <p className="form-subtitle">Consulte evoluções anteriores, diagnósticos e alertas clínicos assistidos por IA.</p>
          </div>

          <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <div style={{ minWidth: '220px' }}>
              <label className="input-label">PACIENTE</label>
              <select
                  className="select-custom"
                  value={petSelecionadoId}
                  onChange={(e) => setPetSelecionadoId(Number(e.target.value))}
              >
                <option value={1}>Thor (Mariana Costa)</option>
                <option value={2}>Luna (Mariana Costa)</option>
                <option value={3}>Simba (Juliana Faria)</option>
              </select>
            </div>
            <Button variant="outlined" size="sm" onClick={() => setModoFallback(!modoFallback)}>
              {modoFallback ? 'Modo Normal (IA)' : 'Simular Fallback'}
            </Button>
            <Button variant="outlined" size="sm" onClick={() => window.print()}>
              🖨️ Imprimir
            </Button>
          </div>
        </header>

        <div className="table-card" style={{ marginBottom: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
              <div className="pet-thumb-circle" style={{ width: 64, height: 64, fontSize: '2.5rem' }}>
                {prontuario.especie === 'Gato' ? '🐈' : '🐕'}
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <h2 style={{ margin: 0, fontSize: '1.4rem', fontWeight: 800 }}>{prontuario.petNome}</h2>
                  <Badge variant="primary">Plano Pet Cuidar Plus</Badge>
                </div>
                <p style={{ margin: '4px 0 0 0', fontSize: '0.82rem', color: 'var(--color-neutral-muted)' }}>
                  {prontuario.especie} • {prontuario.raca} • {prontuario.idade} ({prontuario.peso}) • Microchip: {prontuario.microchip} • Prontuário: {prontuario.prontuarioCod}
                </p>
              </div>
            </div>
            <div style={{ textAlign: 'right', fontSize: '0.85rem' }}>
              <strong>Tutor: {prontuario.tutorNome}</strong>
              <div style={{ color: 'var(--color-neutral-muted)', fontSize: '0.78rem' }}>{prontuario.tutorTel}</div>
              <div style={{ color: 'var(--color-neutral-muted)', fontSize: '0.78rem' }}>{prontuario.tutorEmail}</div>
            </div>
          </div>
        </div>

        {prontuario.alergiasCriticas && (
            <div className="alert-allergy-box">
              <span style={{ fontSize: '1.8rem' }}>🛑</span>
              <div>
                <strong style={{ color: 'var(--color-danger)', fontSize: '0.95rem' }}>
                  ALERTA CRÍTICO: ALERGIA GRAVE REGISTRADA
                </strong>
                <p style={{ margin: '2px 0 0 0', fontSize: '0.85rem', color: 'var(--color-neutral-dark)', lineHeight: 1.45 }}>
                  {prontuario.alergiasCriticas}
                </p>
              </div>
            </div>
        )}

        <div className="recepcao-dashboard-grid">
          <div>
            <div className={`ai-summary-card ${modoFallback ? 'fallback' : 'active'}`}>
              <div className="ai-summary-header">
              <span className={`ai-summary-title ${modoFallback ? 'fallback' : 'active'}`}>
                {modoFallback ? '⚠️ Resumo indisponível no momento' : '✨ Resumo do histórico (Gerado automaticamente por IA)'}
              </span>
                <Badge variant={modoFallback ? 'warning' : 'success'}>
                  {modoFallback ? 'Indisponível' : 'Síntese Clínica'}
                </Badge>
              </div>
              <p className="ai-summary-text">
                {modoFallback
                    ? 'Não foi possível gerar o resumo automático. Consulte os registros do histórico abaixo. O atendimento e o cadastro continuam funcionando normalmente.'
                    : prontuario.resumoHistorico}
              </p>
            </div>

            <h3 style={{ margin: '24px 0 16px 0', fontSize: '1.1rem', fontWeight: 800 }}>
              Atendimentos Anteriores ({prontuario.historico?.length || 0} registros)
            </h3>

            <div className="vet-timeline-wrap">
              {prontuario.historico?.map((item) => (
                  <div key={item.id} className="vet-card-entry">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <strong style={{ fontSize: '0.9rem' }}>
                          {new Date(item.data).toLocaleDateString('pt-BR')} às {new Date(item.data).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                        </strong>
                        <span style={{ fontSize: '0.8rem', color: 'var(--color-neutral-muted)', marginLeft: '8px' }}>
                      • {item.vetNome}
                    </span>
                      </div>
                      <Badge variant="success">● {item.status}</Badge>
                    </div>
                    <div style={{ fontWeight: 800, color: 'var(--color-primary)', marginTop: '6px', fontSize: '0.9rem' }}>
                      {item.tipo}
                    </div>
                    <p style={{ margin: '6px 0 0 0', fontSize: '0.85rem', color: 'var(--color-neutral-dark)', lineHeight: 1.5 }}>
                      {item.descricao}
                    </p>
                  </div>
              ))}
            </div>
          </div>

          <aside className="table-card">
            <form onSubmit={handleSalvarAtendimento}>
              <h3 style={{ margin: '0 0 10px 0', fontSize: '1rem', fontWeight: 800 }}>Registrar atendimento</h3>
              <p style={{ margin: '0 0 16px 0', fontSize: '0.8rem', color: 'var(--color-neutral-muted)' }}>
                Novo registro no prontuário de {prontuario.petNome}.
              </p>

              <div style={{ marginBottom: '14px' }}>
                <label className="input-label">TIPO DE ATENDIMENTO</label>
                <select
                    className="select-custom"
                    value={tipoAtendimento}
                    onChange={(e) => setTipoAtendimento(e.target.value)}
                >
                  <option>Consulta de retorno</option>
                  <option>Consulta de rotina / Check-up</option>
                  <option>Atendimento emergencial</option>
                </select>
              </div>

              <div style={{ marginBottom: '14px' }}>
                <label className="input-label">EVOLUÇÃO CLÍNICA & ANOTAÇÕES</label>
                <textarea
                    className="textarea-custom"
                    style={{ height: '110px' }}
                    placeholder="Descreva a anamnese, exame físico, diagnóstico presumido e prescrições..."
                    value={evolucaoTexto}
                    onChange={(e) => setEvolucaoTexto(e.target.value)}
                    required
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.82rem', marginBottom: '16px' }}>
                <label style={{ display: 'flex', gap: '8px', cursor: 'pointer' }}>
                  <input type="checkbox" defaultChecked /> Requer retorno em 15 dias
                </label>
                <label style={{ display: 'flex', gap: '8px', cursor: 'pointer' }}>
                  <input type="checkbox" defaultChecked /> Emitir notificação no WhatsApp do tutor
                </label>
              </div>

              <Button variant="primary" size="lg" type="submit" style={{ width: '100%' }}>
                ✓ Salvar atendimento
              </Button>
            </form>
          </aside>
        </div>
      </div>
  );
}