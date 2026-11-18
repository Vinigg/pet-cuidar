import React, { useState } from 'react';
import { Button, Badge } from './common/UIComponents';

const FILA_LEMBRETES_INICIAL = [
    { id: 1, pet: 'Thor', tutor: 'Mariana Silva', canal: 'E-mail', status: 'Enviado', horario: '08:30 • Dra. Ana Souza', texto: 'Olá, Mariana Silva! Lembramos que o seu pet Thor tem consulta confirmada hoje às 08:30 no Pet Cuidar.' },
    { id: 2, pet: 'Simba', tutor: 'Juliana Faria', canal: 'E-mail', status: 'Enviado', horario: '09:15 • Dr. Vinícius Lima', texto: 'Olá, Juliana Faria! A consulta de Simba com Dr. Vinícius está confirmada para hoje às 09:15.' },
    { id: 3, pet: 'Billy', tutor: 'Rodrigo Alencar', canal: 'E-mail', status: 'Falhou', horario: '11:30 • Dra. Ana Souza', texto: 'Olá, Rodrigo! A consulta do Billy está agendada para hoje às 11:30.' },
    { id: 4, pet: 'Pipoca', tutor: 'Fernanda Costa', canal: 'E-mail', status: 'Pendente', horario: '14:00 • Dr. Vinícius Lima', texto: 'Olá, Fernanda! Lembramos que a Pipoca tem procedimento agendado para hoje às 14:00.' }
];

export default function LembretesRecepcao() {
    const [fila, setFila] = useState(FILA_LEMBRETES_INICIAL);
    const [modalMensagem, setModalMensagem] = useState(null);
    const [alerta, setAlerta] = useState(null);

    // CONTAGENS REAIS E DINÂMICAS BASEADAS NA LISTA
    const totalEnviados = fila.filter(f => f.status === 'Enviado').length;
    const totalPendentes = fila.filter(f => f.status === 'Pendente').length;
    const totalFalhas = fila.filter(f => f.status === 'Falhou').length;
    const taxaAbertura = fila.length > 0 ? Math.round((totalEnviados / fila.length) * 100) : 0;

    const handleDisparoLote = () => {
        setFila(prev => prev.map(item => ({ ...item, status: 'Enviado' })));
        setAlerta('🚀 Disparo em lote concluído: todos os lembretes foram enviados!');
        setTimeout(() => setAlerta(null), 3500);
    };

    const handleReenviar = (id) => {
        setFila(prev => prev.map(item => item.id === id ? { ...item, status: 'Enviado' } : item));
        setAlerta('✓ Lembrete reenviado com sucesso!');
        setTimeout(() => setAlerta(null), 3000);
    };

    return (
        <div className="clinica-container">
            {alerta && (
                <div className="alert-box success" style={{ marginBottom: '16px' }}>
                    {alerta}
                </div>
            )}

            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <div>
          <span style={{ fontSize: '0.75rem', fontWeight: 800, color: 'var(--color-primary)' }}>
            RECEPÇÃO • NOTIFICAÇÕES PREVENTIVAS
          </span>
                    <h1 className="tutor-main-title" style={{ fontSize: '2rem', margin: '4px 0' }}>Gestão e Envio de Lembretes</h1>
                    <p className="tutor-main-sub">Acompanhe e dispare notificações preventivas para os atendimentos agendados.</p>
                </div>
                <Button variant="primary" size="sm" onClick={handleDisparoLote}>
                    🚀 Disparo em lote
                </Button>
            </header>

            {/* Métricas que batem exatamente com as linhas da tabela */}
            <div className="metrics-grid" style={{ marginBottom: '28px' }}>
                <div className="metric-card">
                    <span className="metric-label">ENVIADOS HOJE</span>
                    <span className="metric-value" style={{ color: 'var(--color-tertiary)' }}>
            {totalEnviados}
          </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-tertiary)' }}>
            ↗ {taxaAbertura}% de taxa de entrega
          </span>
                </div>

                <div className="metric-card">
                    <span className="metric-label">PENDENTES</span>
                    <span className="metric-value">
            {totalPendentes}
          </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>
            Aguardando horário de envio
          </span>
                </div>

                <div className="metric-card">
                    <span className="metric-label">FALHAS DE ENVIO</span>
                    <span className="metric-value" style={{ color: 'var(--color-danger)' }}>
            {totalFalhas}
          </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-danger)' }}>
            {totalFalhas > 0 ? 'Endereço inválido ou sem contato' : 'Nenhuma falha'}
          </span>
                </div>
            </div>

            <div className="table-card">
                <table className="panel-table">
                    <thead>
                    <tr>
                        <th>CONSULTA / PET</th>
                        <th>TUTOR</th>
                        <th>CANAL</th>
                        <th>STATUS DO ENVIO</th>
                        <th style={{ textAlign: 'right' }}>AÇÕES</th>
                    </tr>
                    </thead>
                    <tbody>
                    {fila.map((item) => (
                        <tr key={item.id}>
                            <td>
                                <strong>{item.pet}</strong>
                                <div style={{ fontSize: '0.75rem', color: 'var(--color-neutral-muted)' }}>{item.horario}</div>
                            </td>
                            <td>{item.tutor}</td>
                            <td>{item.canal}</td>
                            <td>
                                <Badge variant={item.status === 'Enviado' ? 'success' : item.status === 'Falhou' ? 'danger' : 'warning'}>
                                    {item.status}
                                </Badge>
                            </td>
                            <td style={{ textAlign: 'right' }}>
                                <Button variant="outlined" size="sm" onClick={() => setModalMensagem(item)}>
                                    Ver mensagem
                                </Button>
                                <Button variant="primary" size="sm" style={{ marginLeft: '8px' }} onClick={() => handleReenviar(item.id)}>
                                    Reenviar
                                </Button>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {modalMensagem && (
                <div className="modal-overlay">
                    <div className="modal-operacional-card">
                        <h3 style={{ margin: '0 0 10px 0' }}>Mensagem de Lembrete: {modalMensagem.pet}</h3>
                        <p style={{ fontSize: '0.85rem', color: 'var(--color-neutral-muted)' }}>
                            Destinatário: {modalMensagem.tutor} ({modalMensagem.canal})
                        </p>
                        <div style={{ backgroundColor: 'var(--color-surface-soft)', padding: '16px', borderRadius: 'var(--radius-md)', margin: '16px 0', fontSize: '0.9rem', lineHeight: '1.5' }}>
                            "{modalMensagem.texto}"
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                            <Button variant="outlined" size="sm" onClick={() => setModalMensagem(null)}>
                                Fechar
                            </Button>
                            <Button variant="primary" size="sm" onClick={() => { handleReenviar(modalMensagem.id); setModalMensagem(null); }}>
                                Disparar Agora
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}