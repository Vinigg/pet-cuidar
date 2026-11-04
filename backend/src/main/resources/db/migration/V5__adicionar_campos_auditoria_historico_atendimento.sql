-- V5__adicionar_campos_auditoria_historico_atendimento.sql
-- Migration para adicionar campos de auditoria e rastreabilidade ao histórico de atendimentos

ALTER TABLE historico_atendimentos ADD COLUMN veterinario_id INTEGER REFERENCES veterinarios (id);

ALTER TABLE historico_atendimentos ADD COLUMN data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE historico_atendimentos ADD COLUMN data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE historico_atendimentos ADD COLUMN veterinario_criacao_id INTEGER REFERENCES veterinarios (id);

ALTER TABLE historico_atendimentos ADD COLUMN veterinario_atualizacao_id INTEGER REFERENCES veterinarios (id);

ALTER TABLE historico_atendimentos ADD COLUMN versao INTEGER DEFAULT 0;

-- Criar índices para melhor performance em queries de auditoria
CREATE INDEX IF NOT EXISTS idx_historico_vet ON historico_atendimentos (veterinario_id);
CREATE INDEX IF NOT EXISTS idx_historico_vet_criacao ON historico_atendimentos (veterinario_criacao_id);
CREATE INDEX IF NOT EXISTS idx_historico_vet_atualizacao ON historico_atendimentos (veterinario_atualizacao_id);
CREATE INDEX IF NOT EXISTS idx_historico_data_atualizacao ON historico_atendimentos (data_atualizacao);
