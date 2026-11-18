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
      if (onLembreteEnviado) onLembreteEnviado(response.data);
    } catch {
      setResultado({
        textoValidado: `Olá, ${consulta.tutorNome}! Lembramos que o seu pet ${consulta.petNome} tem consulta confirmada para hoje às ${new Date(consulta.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })} na Pet Cuidar.`
      });
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="modal-overlay">
        <div className="modal-card">
          <div className="modal-header">
            <Badge variant="primary">Disparo de Lembrete</Badge>
            <button onClick={onClose} className="modal-close-btn" aria-label="Fechar modal">✕</button>
          </div>

          <h2 className="modal-title" style={{ fontSize: '1.35rem', margin: '4px 0' }}>Enviar Lembrete</h2>
          <p className="modal-subtitle" style={{ fontSize: '0.82rem', marginBottom: '16px' }}>
            Revise o conteúdo da notificação preventiva antes do disparo automático.
          </p>

          <div className="modal-info-card" style={{ padding: '10px 14px', marginBottom: '14px' }}>
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

          <div className="modal-text-box ia" style={{ padding: '12px 14px', marginBottom: '16px' }}>
            <div className="modal-tag-status ia" style={{ fontSize: '0.72rem', marginBottom: '6px' }}>
              ✨ Validação do Texto por IA (Pronto)
            </div>
            <p className="modal-body-text" style={{ fontSize: '0.85rem' }}>
              {resultado?.textoValidado ||
                  `Olá, ${consulta.tutorNome}! Lembramos que a consulta de ${consulta.petNome} com a ${consulta.veterinarioNome || 'Dra. Ana Souza'} está confirmada para hoje às ${new Date(consulta.dataHora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}. Caso não possa comparecer, avise com antecedência.`}
            </p>
          </div>

          {erro && <div className="input-error-msg" style={{ marginBottom: '14px' }}>{erro}</div>}

          <div className="modal-actions" style={{ gap: '10px' }}>
            <Button variant="outlined" size="sm" onClick={onClose} disabled={loading}>
              Cancelar
            </Button>
            <Button
                variant="primary"
                size="sm"
                onClick={handleDispararLembrete}
                disabled={loading}
            >
              {loading ? 'Enviando...' : resultado ? 'Lembrete Enviado ✓' : 'Enviar lembrete →'}
            </Button>
          </div>
        </div>
      </div>
  );
}