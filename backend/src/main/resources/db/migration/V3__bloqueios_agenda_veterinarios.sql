CREATE TABLE bloqueios_agenda_veterinario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    veterinario_id BIGINT NOT NULL REFERENCES veterinarios(id),
    inicio TIMESTAMP NOT NULL,
    fim TIMESTAMP NOT NULL,
    motivo VARCHAR(255)
);

CREATE INDEX idx_bloqueios_veterinario_periodo ON bloqueios_agenda_veterinario(veterinario_id, inicio, fim);