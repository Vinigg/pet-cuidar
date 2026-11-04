import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Badge } from './common/UIComponents';

export default function HistoricoPet() {
  const [pets, setPets] = useState([]);
  const [petSelecionadoId, setPetSelecionadoId] = useState('');
  const [prontuario, setProntuario] = useState(null);
  const [loading, setLoading] = useState(false);

  // 1. Carrega a lista de pacientes disponíveis
  useEffect(() => {
    async function carregarPets() {
      try {
        const response = await axios.get('http://localhost:8080/pets/opcoes');
        setPets(response.data || []);
        if (response.data?.length > 0) {
          setPetSelecionadoId(response.data[0].petId);
        }
      } catch (err) {
        console.error('Erro ao listar pets:', err);
      }
    }
    carregarPets();
  }, []);

  // 2. Busca histórico e resumo de IA do pet selecionado
  const carregarHistorico = async (id) => {
    if (!id) return;
    setLoading(true);
    try {
      const response = await axios.get(`http://localhost:8080/pets/${id}/resumo-historico`);
      setProntuario(response.data);
    } catch (err) {
      console.error('Erro ao buscar histórico do pet:', err);
      setProntuario(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (petSelecionadoId) {
      carregarHistorico(petSelecionadoId);
    }
  }, [petSelecionadoId]);

  const isFallbackResumo = prontuario && prontuario.resumoHistorico?.includes('Resumo temporariamente indisponível');

  return (
      <div className="history-container">
        <div className="history-card">
          {/* Topo com Seletor de Paciente */}
          <header className="history-header">
            <div>
              <Badge variant="primary">Prontuário Veterinário</Badge>
              <h1 className="form-title" style={{ marginTop: '8px' }}>Histórico do Pet</h1>
              <p className="form-subtitle">Consulte evoluções anteriores e diagnósticos com resumo assistido por IA.</p>
            </div>

            <div style={{ minWidth: '240px' }}>
              <label className="input-label" style={{ marginBottom: '6px', display: 'block' }}>PACIENTE</label>
              <select
                  className="select-custom"
                  value={petSelecionadoId}
                  onChange={(e) => setPetSelecionadoId(e.target.value)}
              >
                {pets.map(p => (
                    <option key={p.petId} value={p.petId}>
                      {p.petNome} ({p.tutorNome})
                    </option>
                ))}
              </select>
            </div>
          </header>

          {loading ? (
              <div className="empty-state">Carregando prontuário do paciente...</div>
          ) : !prontuario ? (
              <div className="empty-state">Nenhum prontuário encontrado para este paciente.</div>
          ) : (
              <div>
                {/* Alerta de Alergia Crítica se houver */}
                {prontuario.alergiasCriticas && (
                    <div className="critical-allergy-alert">
                      <span>⚠️ ATENÇÃO:</span>
                      <span>Alergias ou restrições críticas: {prontuario.alergiasCriticas}</span>
                    </div>
                )}

                {/* Card com Resumo de IA ou Fallback */}
                <div className={`ai-summary-card ${isFallbackResumo ? 'fallback' : 'active'}`}>
                  <div className="ai-summary-header">
                    <h3 className={`ai-summary-title ${isFallbackResumo ? 'fallback' : 'active'}`}>
                      {isFallbackResumo ? '⚠️ Resumo Clínico em Contingência' : '✨ Resumo Clínico Inteligente (IA)'}
                    </h3>
                    <Badge variant={isFallbackResumo ? 'warning' : 'success'}>
                      {isFallbackResumo ? 'Modo Fallback' : 'Síntese Automática'}
                    </Badge>
                  </div>
                  <p className="ai-summary-text">
                    {prontuario.resumoHistorico || 'Nenhum resumo clínico gerado para este paciente.'}
                  </p>
                </div>

                {/* Linha do Tempo dos Atendimentos Anteriores */}
                <h2 className="timeline-section-title">Evoluções Anteriores</h2>

                {(!prontuario.historico || prontuario.historico.length === 0) ? (
                    <div className="empty-state" style={{ padding: '24px' }}>
                      Nenhum atendimento anterior registrado para este animal.
                    </div>
                ) : (
                    <div className="timeline">
                      {prontuario.historico.map((item, index) => (
                          <div key={item.id || index} className="timeline-item">
                            <span className="timeline-dot" />
                            <div className="timeline-date">
                              {item.data ? new Date(item.data).toLocaleDateString('pt-BR') : 'Data não informada'}
                            </div>
                            <p className="timeline-desc">{item.descricao}</p>
                          </div>
                      ))}
                    </div>
                )}
              </div>
          )}
        </div>
      </div>
  );
}