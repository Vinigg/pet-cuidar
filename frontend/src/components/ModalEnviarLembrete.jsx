import React, { useEffect, useRef, useState } from 'react';
import { dispararLembrete } from '../api/api';
import { Button, Badge } from './common/UIComponents';

export default function ModalEnviarLembrete({ isOpen, onClose, consulta, onLembreteEnviado, resultadoInicial, permitirEnvio = true }) {
    const [loading, setLoading] = useState(false);
    const [resultado, setResultado] = useState(null);
    const [erro, setErro] = useState('');
    const enviando = useRef(false);
    const requisicao = useRef(0);

    useEffect(() => {
        requisicao.current += 1;
        enviando.current = false;
        setLoading(false);
        setResultado(resultadoInicial?.status === 'NAO_GERADO' ? null : resultadoInicial || null);
        setErro('');
        return () => { requisicao.current += 1; };
    }, [isOpen, consulta?.id, resultadoInicial]);

    if (!isOpen || !consulta) return null;

    const enviar = async () => {
        if (!permitirEnvio || enviando.current || resultado?.status === 'ENVIADO') return;
        enviando.current = true;
        const atual = requisicao.current;
        setLoading(true);
        setErro('');
        try {
            const { data } = await dispararLembrete(consulta.id);
            if (atual !== requisicao.current) return;
            setResultado(data);
            if (data.status === 'ENVIADO') {
                onLembreteEnviado?.(data);
            } else {
                setErro(data.ultimoErro || 'O envio não foi confirmado pelo servidor.');
            }
        } catch (e) {
            if (atual === requisicao.current) {
                setErro(e.response?.data?.mensagem || 'Não foi possível enviar o lembrete. Tente novamente.');
            }
        } finally {
            if (atual === requisicao.current) {
                enviando.current = false;
                setLoading(false);
            }
        }
    };

    const enviado = resultado?.status === 'ENVIADO';
    const dataHora = new Date(consulta.dataHora);
    return (
        <div className="modal-overlay">
            <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="titulo-lembrete">
                <div className="modal-header">
                    <Badge variant="primary">Disparo de Lembrete</Badge>
                    <button type="button" onClick={onClose} disabled={loading} className="modal-close-btn" aria-label="Fechar modal">✕</button>
                </div>
                <h2 id="titulo-lembrete" className="modal-title" style={{ fontSize: '1.35rem', margin: '4px 0' }}>Enviar Lembrete</h2>
                <p className="modal-subtitle">O servidor gera, valida e envia a mensagem por e-mail ao tutor.</p>
                <div className="modal-info-card">
                    <div className="modal-info-col"><span className="modal-info-label">PACIENTE</span><span className="modal-info-value">{consulta.petNome}</span></div>
                    <div className="modal-info-col"><span className="modal-info-label">TUTOR</span><span className="modal-info-value">{consulta.tutorNome}</span></div>
                    <div className="modal-info-col"><span className="modal-info-label">DATA E HORÁRIO</span><span className="modal-info-value">{dataHora.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</span></div>
                </div>
                <div className={`modal-text-box ${resultado?.usouFallback ? 'fallback' : 'ia'}`}>
                    <div className={`modal-tag-status ${resultado?.usouFallback ? 'fallback' : 'ia'}`}>
                        {resultado?.textoEnviado ? resultado.usouFallback ? 'Template padrão validado' : 'Texto validado pelo servidor' : permitirEnvio ? 'Mensagem será gerada no envio' : 'Sem texto de lembrete registrado'}
                    </div>
                    <p className="modal-body-text">{resultado?.textoEnviado || (permitirEnvio ? 'Após o processamento, o texto validado será exibido aqui.' : 'Nenhuma mensagem registrada para esta consulta.')}</p>
                </div>
                {enviado && <p role="status" className="alert-box success">Lembrete enviado por e-mail.</p>}
                {erro && <div className="input-error-msg" role="alert">{erro}</div>}
                {!erro && resultado?.status === 'FALHOU' && <p className="input-error-msg" role="alert">{resultado.ultimoErro || 'A entrega deste lembrete falhou.'}</p>}
                <div className="modal-actions" style={{ gap: 10 }}>
                    <Button variant="outlined" size="sm" onClick={onClose} disabled={loading}>{enviado ? 'Fechar' : 'Cancelar'}</Button>
                    <Button variant="primary" size="sm" onClick={enviar} disabled={loading || enviado || !permitirEnvio}>
                        {loading ? 'Enviando...' : enviado ? 'Lembrete Enviado ✓' : permitirEnvio ? 'Enviar lembrete →' : 'Envio indisponível'}
                    </Button>
                </div>
            </div>
        </div>
    );
}
