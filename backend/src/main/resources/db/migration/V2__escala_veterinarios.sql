ALTER TABLE veterinarios ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT 1;

CREATE TABLE horarios_atendimento_veterinario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    veterinario_id BIGINT NOT NULL REFERENCES veterinarios(id),
    dia_semana VARCHAR(255) NOT NULL CHECK (dia_semana IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    hora_inicio VARCHAR(5) NOT NULL,
    hora_fim VARCHAR(5) NOT NULL,
    CHECK (hora_inicio < hora_fim)
);

CREATE INDEX idx_horarios_veterinario ON horarios_atendimento_veterinario(veterinario_id);

INSERT INTO horarios_atendimento_veterinario (veterinario_id, dia_semana, hora_inicio, hora_fim)
SELECT veterinarios.id, dias.dia, '08:00', '18:00'
FROM veterinarios CROSS JOIN (
    SELECT 'MONDAY' AS dia UNION ALL SELECT 'TUESDAY' UNION ALL SELECT 'WEDNESDAY'
    UNION ALL SELECT 'THURSDAY' UNION ALL SELECT 'FRIDAY' UNION ALL SELECT 'SATURDAY'
    UNION ALL SELECT 'SUNDAY'
) dias;