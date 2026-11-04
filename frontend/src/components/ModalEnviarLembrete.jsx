import React, { useState } from 'react';
import axios from 'axios';
import { Button, Badge } from './common/UIComponents';

export default function ModalEnviarLembrete({ isOpen, onClose, consulta, onLembreteEnviado }) {
  const [loading, setLoading] = useState(false);
  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState(null);

  if (!isOpen || !consulta) return null;

  const handleDispararLembrete = async () => {
    setLoading(true);
    setErro(null);
    try {
      const response = await axios.post(`http://localhost:8080/consultas/${consulta.id}/lembrete`);
      setResultado(response.data);
      if (onLembreteEnviado) {
        onLembreteEnviado(response.data);
      }
    } catch (err) {
      setErro(err.response?.data?.mensagem || 'Erro ao processar o envio do lembrete.');
    } finally {
      setLoading(false);
    }
  };

  const isFallback = resultado && (
      resultado.textoValidado?.includes('Lembrete padrão') ||
      resultado.statusEnvio === 'FALHOU' ||
      resultado.statusEnvioLembrete === 'FALHOU'
  );

  return (
      <div className="modal-overlay">
        <div className="modal-card">
          <div className="modal-header">
            <Badge variant="primary">Lembrete de Consulta</Badge>
            <button onClick={onClose} className="modal-close-btn" aria-label="Fechar modal">✕</button>
          </div>

          <h2 className="modal-title">Disparo de Lembrete</h2>
          <p className="modal-subtitle">
            Revise a mensagem antes do envio automático ao tutor do paciente.
          </p>

          <div className="modal-info-card">
            <div className="modal-info-col">
              <span className="modal-info-label">PACIENTE</span>
              <span className="modal-info-value">{consulta.petNome}</span>
            </div>
            <div className="modal-info-col">
              <span className="modal-info-label">TUTOR</span>
              <span className="modal-info-value">{consulta.tutorNome}</span>
            </div>
            <div className="modal-info-col">
              <span className="modal-info-label">HORÁRIO</span>
              <span className="modal-info-value">
              {new Date(consulta.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
            </span>
            </div>
          </div>

          <div className={`modal-text-box ${isFallback ? 'fallback' : 'ia'}`}>
            <div className={`modal-tag-status ${isFallback ? 'fallback' : 'ia'}`}>
              {resultado ? (
                  isFallback ? '⚠️ Modo Contingência: Template Padrão' : '✨ Texto Personalizado por IA (Validado)'
              ) : (
                  'Mensagem pronta para processamento'
              )}
            </div>

            <p className="modal-body-text">
              {resultado?.textoValidado || resultado?.textoGerado || (
                  `Olá, ${consulta.tutorNome}! A consulta de ${consulta.petNome} está confirmada para o dia ${new Date(consulta.dataHora).toLocaleDateString('pt-BR')} às ${new Date(consulta.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}. Em caso de imprevisto, responda a esta mensagem.`
              )}
            </p>
          </div>

          {erro && <div className="input-error-msg" style={{ marginBottom: '16px' }}>{erro}</div>}

          <div className="modal-actions">
            <Button variant="outlined" onClick={onClose} disabled={loading}>
              Cancelar
            </Button>
            <Button
                variant={resultado ? 'secondary' : 'primary'}
                onClick={handleDispararLembrete}
                disabled={loading}
            >
              {loading ? 'Processando...' : resultado ? 'Lembrete Enviado ✓' : 'Enviar Lembrete Agora'}
            </Button>
          </div>
        </div>
      </div>
  );
}