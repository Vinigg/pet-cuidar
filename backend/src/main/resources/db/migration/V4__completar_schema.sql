CREATE TABLE IF NOT EXISTS tutores (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    nome     VARCHAR(255) NOT NULL,
    email    VARCHAR(255) NOT NULL UNIQUE,
    telefone VARCHAR(255),
    cpf      VARCHAR(255) UNIQUE
);

CREATE TABLE IF NOT EXISTS pets (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    tutor_id          INTEGER NOT NULL REFERENCES tutores (id),
    nome              VARCHAR(255) NOT NULL,
    especie           VARCHAR(255),
    idade             INTEGER,
    alergias_criticas VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS veterinarios (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    nome         VARCHAR(255) NOT NULL,
    especialidade VARCHAR(255),
    versao       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS consultas (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    pet_id         INTEGER NOT NULL REFERENCES pets (id),
    veterinario_id INTEGER NOT NULL REFERENCES veterinarios (id),
    data_hora      TIMESTAMP NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'AGENDADA'
        CHECK (status IN ('AGENDADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA', 'NAO_COMPARECEU'))
);

CREATE TABLE IF NOT EXISTS historico_atendimentos (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    pet_id      INTEGER NOT NULL REFERENCES pets (id),
    consulta_id INTEGER REFERENCES consultas (id),
    descricao   VARCHAR(2000),
    resumo_ia   VARCHAR(1000),
    data        TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS lembretes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    consulta_id     INTEGER NOT NULL UNIQUE REFERENCES consultas (id),
    canal           VARCHAR(255) NOT NULL DEFAULT 'EMAIL',
    texto_gerado_ia VARCHAR(1000),
    texto_validado  VARCHAR(1000),
    status_envio    VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status_envio IN ('PENDENTE', 'ENVIADO', 'FALHOU')),
    data_envio      TIMESTAMP,
    tentativas      INTEGER DEFAULT 0,
    ultimo_erro     VARCHAR(1000),
    data_ultima_tentativa TIMESTAMP,
    usou_fallback   BOOLEAN NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS predicoes_no_show (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    consulta_id   INTEGER NOT NULL UNIQUE REFERENCES consultas (id),
    score_cru     DECIMAL(4,3) CHECK (score_cru   IS NULL OR (score_cru   >= 0 AND score_cru   <= 1)),
    score_usado   DECIMAL(4,3) CHECK (score_usado IS NULL OR (score_usado >= 0 AND score_usado <= 1)),
    usou_fallback BOOLEAN NOT NULL DEFAULT 0,
    motivo        VARCHAR(500),
    data_calculo  TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pets_tutor            ON pets (tutor_id);
CREATE INDEX IF NOT EXISTS idx_consultas_pet         ON consultas (pet_id);
CREATE INDEX IF NOT EXISTS idx_consultas_vet_data    ON consultas (veterinario_id, data_hora);
CREATE INDEX IF NOT EXISTS idx_historico_pet         ON historico_atendimentos (pet_id);