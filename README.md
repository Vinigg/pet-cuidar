# Pet Cuidar — Sistema de Agendamento (GrowUp)

Codigo do MVP descrito na Entrega Parcial GrowUp, baseado no PRD 1 (Desafio DAS,
Turma A): agendamento de consultas para a clinica veterinaria "Pet Cuidar", com
lembretes gerados por IA, predicao de risco de falta (no-show) e resumo do
historico do pet para o veterinario.

Stack escolhida pelo grupo:
- **Back-end:** Java 17 + Spring Boot 3 (Maven)
- **Front-end:** React (JavaScript) + HTML/CSS
- **Banco de dados:** SQLite (arquivo local `petcuidar.db`)
- **Cloud alvo:** AWS (ver `DEPLOY.md`)

## Estrutura

```
pet-cuidar/
├── backend/    -> API REST em Spring Boot
├── frontend/   -> aplicacao React (Create React App)
├── README.md
└── DEPLOY.md   -> notas de deploy na AWS
```

## Rodando o back-end

Pre-requisitos: Java 17+ e Maven.

```bash
cd backend
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`. O banco SQLite (`petcuidar.db`) e criado
automaticamente na raiz de `backend/` na primeira execucao, com dois
veterinarios de exemplo (`DataSeeder`).

### Cadastro e escala de veterinarios

A API permite cadastrar/editar/inativar profissionais, manter periodos semanais
de atendimento e bloquear datas de ausencia. Disponibilidade, agendamento e
reagendamento respeitam a escala individual. A aba **Veterinarios** no frontend
permite cadastrar profissionais com um ou mais periodos por dia, incluindo
intervalos de almoco. A opcao **Atualizar** seleciona profissionais ativos ou
inativos para editar dados, situacao e escala. Dados e horarios possuem botoes
de salvar separados. Bloqueios por data seguem disponiveis pela API.

Contrato, regras e cuidados com a migracao do SQLite:
[veterinarios-agenda.md](docs/veterinarios-agenda.md). Exemplos de requisicoes:
[veterinarios-agenda.http](backend/requests/veterinarios-agenda.http).
Faca backup do banco antes de atualizar um ambiente com dados reais. O projeto
ainda precisa de autenticacao e autorizacao do perfil da recepcao para producao.

## Rodando o front-end

Pre-requisitos: Node.js 18+.

```bash
cd frontend
cp .env.example .env
npm install
npm start
```

A aplicacao sobe em `http://localhost:3000` e consome a API pela URL definida
em `REACT_APP_API_URL` (`.env`).

## Fluxo para testar rapidamente

1. Aba **Cadastro**: cadastre um tutor com pelo menos um pet (anote o `id` do
   pet retornado, se quiser consultar via API/Postman).
2. Aba **Agendar consulta**: escolha um veterinario (os dois de exemplo ja
   existem), uma data e um horario livre; informe o id do pet cadastrado.
3. Aba **Painel do dia**: veja a consulta criada, calcule o risco de falta e
   dispare o lembrete (gerado por IA, com fallback automatico).
4. Aba **Historico do pet**: consulte o resumo gerado por IA e o historico
   completo do pet.
5. Aba **Veterinarios**: informe nome, especialidade e ao menos um dia de
  atendimento; cadastre os periodos e confirme o cadastro. O profissional
  fica disponivel ao abrir a aba **Agendar consulta**.

## Integracoes de IA, predicao e e-mail

O modo local funciona sem credenciais externas: `LLM_PROVIDER=simulador`
(padrao) e `EMAIL_PROVIDER=local`. Lembrete, resumo do historico e predicao de
no-show contam com timeout, circuit breaker proprio de cada integracao e
fallback deterministico: falha de IA nunca vira erro 5xx (a resposta sai com
`usouFallback=true` ou score indisponivel). Detalhes em
[docs/resiliencia-ia.md](docs/resiliencia-ia.md).

O provedor de IA e escolhido por `LLM_PROVIDER` e exatamente um cliente e
registrado; valor invalido impede a inicializacao com mensagem clara. Cada
provedor usa as proprias variaveis:

- **`openai`** (API compativel com `/chat/completions`): `IA_OPENAI_API_KEY`,
  `IA_OPENAI_MODEL` e, se necessario, `IA_OPENAI_BASE_URL`.
- **`llm-externo`**: `LLM_API_KEY` e, se necessario, `LLM_MODEL`,
  `LLM_BASE_URL`, `LLM_EFFORT` e `LLM_MAX_TOKENS`. Sem chave, a aplicacao sobe
  e os lembretes saem pelo template.

Exemplo: `LLM_PROVIDER=llm-externo` e `LLM_API_KEY=<sua-chave>`.
- `NOSHOW_PREDICTOR_BASE_URL` aponta para o microservico FastAPI em
  `microservico-predicao/`; falha, timeout ou resposta invalida retornam score
  indisponivel, nunca um risco inventado.
- `EMAIL_PROVIDER=resend`, `RESEND_API_KEY` e `EMAIL_FROM` habilitam o envio
  transacional. O fluxo usa o texto validado, chave de idempotencia e ate tres
  tentativas com backoff exponencial.

O job de lembretes fica desabilitado por padrao enquanto o e-mail usa o
simulador local. Com um provedor real configurado, defina
`LEMBRETE_JOB_ENABLED=true`; ele procura consultas nas proximas 24 horas no
fuso `America/Recife`. Consultas canceladas e lembretes ja processados nao
sao enviados novamente. Todas as credenciais devem ficar em variaveis de
ambiente e nunca ser adicionadas ao Git.

| Variavel | Provedor | Padrao | Descricao |
|---|---|---|---|
| `LLM_PROVIDER` | — | `simulador` | `simulador`, `openai` ou `llm-externo` |
| `IA_OPENAI_API_KEY` | `openai` | (vazio) | Chave da API. Nunca versionar |
| `IA_OPENAI_MODEL` | `openai` | `gpt-4o-mini` | Modelo |
| `IA_OPENAI_BASE_URL` | `openai` | `https://api.openai.com/v1` | Endpoint |
| `LLM_API_KEY` | `llm-externo` | (vazio) | Chave da API. Nunca versionar |
| `LLM_MODEL` | `llm-externo` | ver `application.properties` | Modelo |
| `LLM_BASE_URL` | `llm-externo` | ver `application.properties` | Endpoint |
| `LLM_EFFORT` | `llm-externo` | `low` | Nivel de esforco do modelo |
| `LLM_MAX_TOKENS` | `llm-externo` | `2048` | Teto de tokens da resposta |

Timeout e circuit breaker podem ser ajustados por integracao
(`ia.lembrete.*`, `ia.resumo.*`, `ia.predicao.*`); sem ajuste valem
`ia.timeout-ms=5000`, `ia.circuit-breaker.max-falhas=3` e
`ia.circuit-breaker.reset-ms=30000`.

Detalhes de deploy e configuracao de segredos: [DEPLOY.md](DEPLOY.md). Exemplos
de requisicoes da API: [consultas-lembretes.http](backend/requests/consultas-lembretes.http).

## Endpoints principais da API

Erros seguem o formato padrao `{status, mensagem, timestamp}`; a tabela de
quando cada codigo e usado (400, 404, 409, 500, 502, 504) esta em
[docs/erros-api.md](docs/erros-api.md).

| Metodo | Endpoint | Descricao |
|---|---|---|
| POST | `/tutores` | Cadastra tutor e pet(s) |
| GET | `/veterinarios` | Lista veterinarios |
| GET | `/veterinarios/{id}/disponibilidade?data=` | Horarios livres/ocupados no dia |
| POST | `/consultas` | Cria agendamento (valida conflito de horario) |
| PATCH | `/consultas/{id}/status` | Transiciona status (inclui confirmar e cancelar) conforme a máquina de estados |
| PATCH | `/consultas/{id}/reagendamento` | Altera data/hora revalidando disponibilidade e conflito |
| GET | `/consultas?data=` | Lista consultas do dia (painel da recepcionista) |
| GET | `/consultas/{id}/predicao-no-show` | Score de risco de falta + motivo |
| POST | `/consultas/{id}/lembrete` | Gera e valida o lembrete com fallback, entrega de forma idempotente; retorna 409 se a consulta estiver cancelada, 502 se o e-mail nao for entregue e 504 se o provedor de e-mail nao responder no tempo limite |
| GET | `/pets/{id}/historico` | Historico completo do pet |
| GET | `/pets/{id}/historico/resumo` | Resumo gerado por IA + link do historico |

Fluxos de status, cancelamento e reagendamento: `docs/consulta-estados.md`.
Exemplos de requisicoes: `backend/requests/consultas-lembretes.http`.

## Limitacoes conhecidas do MVP

- Sem autenticacao/login implementados (o documento de arquitetura previa JWT
  para recepcionista/veterinario — fica como proxima etapa). Por isso as
  operacoes "da recepcao" (status, cancelamento, reagendamento) nao estao
  restritas por perfil.
- Nao ha job automatico de lembretes (PC-029); o envio e disparado pela API.
- SQLite e um banco em arquivo unico: adequado para o MVP e para demonstracao,
  mas nao recomendado para multiplas instancias do back-end rodando em
  paralelo (ver `DEPLOY.md`).
- Autenticacao/autorizacao por JWT ainda nao foi implementada; portanto os
  endpoints devem permanecer em ambiente controlado ate essa frente ser entregue.
- O adapter Resend e o adapter de LLM estao implementados, mas so ficam
  homologados depois de uma validacao explicita com contas e credenciais reais.


## Validando o cadastro de tutor e pets

Em `backend/`, execute `mvn test`. Use JDK 17 ou 21: o Lombok gerenciado pelo
Spring Boot 3.2.5 nao compila com JDK 25. Os testes de cadastro usam um arquivo
SQLite exclusivo em `target/`, sem alterar o banco de desenvolvimento.
Depois inicie a API com `mvn spring-boot:run`.

Exemplos de requisicoes estao em `backend/requests/cadastro-tutor-pet.http`.
Podem ser usados com um cliente HTTP que aceite esse formato ou copiados
para o Postman. O cadastro valido retorna `201`, com o ID do tutor e os IDs
dos pets; a resposta nao repete o tutor dentro de cada pet nem inclui CPF.

Nome do tutor, e-mail valido e pelo menos um pet com nome sao obrigatorios.
Pet nulo, idade negativa e alergias com mais de 1000 caracteres retornam
`400`. CPF e opcional: vazio ou composto por espacos e salvo como `null`.
E-mail ou CPF ja cadastrado retorna `409`, no formato padrao da API
(`status`, `mensagem`, `timestamp`). O cadastro inteiro e transacional:
falha ao persistir um pet desfaz tambem o tutor e os outros pets.
