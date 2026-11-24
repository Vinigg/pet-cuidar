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
├── backend/                -> API REST em Spring Boot
├── frontend/               -> aplicacao React (Create React App)
├── microservico-predicao/  -> servico Python (FastAPI) de predicao de no-show
├── docs/                   -> contratos, regras e roteiros
├── docker-compose.yml      -> back-end + predicao rodando juntos
├── README.md
└── DEPLOY.md               -> notas de deploy na AWS
```

## Rodando tudo junto (Docker)

Na primeira configuração, copie o [exemplo da raiz](.env.example) para `.env` ao lado do `docker-compose.yml`. Preencha os valores locais nesse arquivo. Depois inicie o back-end e o serviço de predição.

```bash
docker compose up --build   # back-end em http://localhost:8080 + predicao (rede interna)
```

Depois suba o front com `npm start` (secao abaixo). O roteiro de teste de
ponta a ponta esta em [docs/teste-ponta-a-ponta.md](docs/teste-ponta-a-ponta.md).

## Rodando o back-end

Pré-requisitos de Java 17 e Maven.

Na primeira configuração, copie [backend/.env.example](backend/.env.example) para `backend/.env`. Preencha o arquivo local com valores sem aspas. O perfil `local` carrega esse arquivo quando você inicia a API de dentro de `backend/`.

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

A API abre em `http://localhost:8080`. O exemplo usa o banco SQLite `petcuidar-login-teste.db` dentro de `backend/`.

### E-mails locais com Mailpit

Os exemplos de ambiente usam o provedor SMTP e a caixa local Mailpit. Para subir os serviços com Docker, execute na raiz.

```bash
docker compose --profile local-email up -d --build
```

Abra http://localhost:8025 para ver os códigos enviados. Use o e-mail de `AUTH_ADMIN_EMAIL` na entrada do Admin. Para testar Tutor, use **Criar conta de tutor** com outro endereço, como `tutor@petcuidar.local`, e leia o código na mesma caixa Mailpit. O Admin cadastra colaboradores e vincula veterinários em **Contas da equipe**. Cada perfil precisa de um endereço próprio.

Para executar a API pelo Maven, inicie apenas o Mailpit com Docker e use `SMTP_HOST=localhost` no `backend/.env`.

```bash
docker compose --profile local-email up -d mailpit
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Dentro do Compose, o back-end usa `SMTP_HOST=mailpit`. O Mailpit captura mensagens de qualquer destinatário e permite ler os códigos no navegador. As portas da caixa de desenvolvimento estão vinculadas ao endereço local da máquina.
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
Faca backup do banco antes de atualizar um ambiente com dados reais.

## Rodando o front-end

Pre-requisitos: Node.js 18+.

```bash
cd frontend
cp .env.example .env
npm install
npm start
```

A aplicação sobe em `http://localhost:3000`. Em desenvolvimento, o React encaminha as chamadas à API local na porta 8080. Para um build de produção, veja [Autenticação por e-mail no deploy](DEPLOY.md#autenticação-por-e-mail).

## Login e painéis

Informe o e-mail cadastrado e digite o código de oito dígitos recebido. O código vale por dez minutos e só pode ser usado uma vez. Com `EMAIL_PROVIDER=smtp`, os códigos ficam na caixa Mailpit em http://localhost:8025. Para entrega externa, configure `EMAIL_PROVIDER=resend`, `EMAIL_FROM` e `RESEND_API_KEY`. O provedor `local` apenas simula o envio.

| Perfil | Área |
| --- | --- |
| Tutor | Pets e consultas da própria conta. O autocadastro verifica o e-mail antes de criar a conta. |
| Veterinário | Histórico de pets vinculados às suas consultas e registro de atendimentos. |
| Colaborador | Painel do dia, agendamento e cadastro de tutores e pets. |
| Admin | Gestão de veterinários e cadastro de contas da equipe. |

O Admin inicial entra com o e-mail configurado em `AUTH_ADMIN_EMAIL`. Em **Veterinários**, cadastre o profissional e a escala. Em **Contas da equipe**, vincule o e-mail do veterinário e cadastre os colaboradores.

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

Rotas privadas exigem a sessão retornada por `POST /auth/verificar`. Em chamadas diretas, envie o cookie `pc_sessao` e, para métodos que alteram dados, o cabeçalho `X-Pet-Cuidar: 1`.

Erros seguem o formato padrao `{status, mensagem, timestamp}`; a tabela de
quando cada codigo e usado (400, 404, 409, 500, 502, 504) esta em
[docs/erros-api.md](docs/erros-api.md).

| Metodo | Endpoint | Descricao |
|---|---|---|
| POST | `/auth/codigo` | Solicita código por e-mail para entrada ou autocadastro |
| POST | `/auth/verificar` | Verifica o código e retorna a sessão ou a prova de cadastro |
| POST | `/tutores` | Cadastra tutor e pet(s) pelo perfil Colaborador |
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

- Nao ha job automatico de lembretes (PC-029); o envio e disparado pela API.
- SQLite e um banco em arquivo unico: adequado para o MVP e para demonstracao,
  mas nao recomendado para multiplas instancias do back-end rodando em
  paralelo (ver `DEPLOY.md`).
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
