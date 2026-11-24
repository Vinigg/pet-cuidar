CREATE TABLE contas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL UNIQUE,
    nome TEXT NOT NULL,
    perfil TEXT NOT NULL CHECK (perfil IN ('TUTOR', 'VETERINARIO', 'COLABORADOR', 'ADMIN')),
    tutor_id INTEGER UNIQUE REFERENCES tutores(id),
    veterinario_id INTEGER UNIQUE REFERENCES veterinarios(id),
    CHECK ((perfil = 'TUTOR') = (tutor_id IS NOT NULL)),
    CHECK ((perfil = 'VETERINARIO') = (veterinario_id IS NOT NULL))
);

INSERT INTO contas (email, nome, perfil, tutor_id)
SELECT lower(trim(email)), min(nome), 'TUTOR', min(id)
FROM tutores
WHERE trim(email) <> ''
GROUP BY lower(trim(email))
HAVING count(*) = 1;

CREATE TABLE desafios_email (
    email TEXT NOT NULL,
    finalidade TEXT NOT NULL CHECK (finalidade IN ('LOGIN', 'CADASTRO')),
    digest TEXT,
    criado_em INTEGER NOT NULL,
    expira_em INTEGER NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (email, finalidade)
);

CREATE TABLE credenciais_auth (
    digest TEXT PRIMARY KEY,
    conta_id INTEGER REFERENCES contas(id),
    email TEXT,
    expira_em INTEGER NOT NULL,
    CHECK ((conta_id IS NOT NULL) <> (email IS NOT NULL))
);

CREATE INDEX idx_credenciais_auth_expira ON credenciais_auth(expira_em);
CREATE INDEX idx_credenciais_auth_conta ON credenciais_auth(conta_id);
CREATE INDEX idx_desafios_email_expira ON desafios_email(expira_em);
