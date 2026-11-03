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

## Sobre a integracao de IA neste repositorio

A geracao de lembretes tem integracao real com LLM (API da Anthropic, SDK
oficial `anthropic-java`), ativada por variavel de ambiente. Sem configuracao,
o backend usa um **simulador local** (`LembreteIaSimulator`), que nao e uma
integracao com LLM e serve para rodar o projeto sem chaves. Em qualquer modo,
timeout, circuit breaker, guardrail deterministico e template de fallback
estao implementados: falha da IA nunca impede o envio do lembrete.

| Variavel | Padrao | Descricao |
|---|---|---|
| `LLM_PROVIDER` | `simulador` | `anthropic` para usar o LLM real |
| `LLM_API_KEY` | (vazio) | Chave da API. Nunca versionar |
| `LLM_MODEL` | `claude-opus-5-5` | Modelo |
| `LLM_EFFORT` | `low` | Nivel de esforco do modelo |
| `LLM_MAX_TOKENS` | `2048` | Teto de tokens da resposta |
| `LLM_BASE_URL` | `https://api.anthropic.com` | Endpoint |

Detalhes em `docs/resiliencia-ia.md`. O resumo de historico do pet
(`ResumoHistoricoService`) continua simulado.

## Endpoints principais da API

| Metodo | Endpoint | Descricao |
|---|---|---|
| POST | `/tutores` | Cadastra tutor e pet(s) |
| GET | `/veterinarios` | Lista veterinarios |
| GET | `/veterinarios/{id}/disponibilidade?data=` | Horarios livres/ocupados no dia |
| POST | `/consultas` | Cria agendamento (valida conflito de horario) |
| PATCH | `/consultas/{id}/status` | Transiciona status (inclui confirmar e cancelar) conforme a máquina de estados |
| PATCH | `/consultas/{id}/reagendamento` | Altera data/hora revalidando disponibilidade e conflito |
| GET | `/consultas?data=` | Lista consultas do dia (painel da recepcionista) |
| GET | `/consultas/{id}` | Detalhes de uma consulta com pet, tutor e veterinario (somente leitura) |
| GET | `/consultas/{id}/predicao-no-show` | Score de risco de falta + motivo |
| POST | `/consultas/{id}/lembrete` | Gera e "envia" o lembrete (IA + guardrail + fallback; 409 se cancelada) |
| GET | `/pets/{id}/historico` | Historico completo do pet |
| GET | `/pets/{id}/historico/resumo` | Resumo gerado por IA + link do historico |

Fluxos de status, cancelamento e reagendamento: `docs/consulta-estados.md`.
Detalhamento da consulta (API e tela): `docs/consulta-detalhes.md`.
Exemplos de requisicoes: `backend/requests/consultas-lembretes.http`.

## Limitacoes conhecidas do MVP

- Sem autenticacao/login implementados (o documento de arquitetura previa JWT
  para recepcionista/veterinario — fica como proxima etapa). Por isso as
  operacoes "da recepcao" (status, cancelamento, reagendamento) nao estao
  restritas por perfil.
- Nao ha job automatico de lembretes (PC-029); o envio e disparado pela API.
- O CORS (`CorsConfig`) nao libera o metodo PATCH. Hoje o front-end nao usa
  PATCH, mas uma tela futura de status/reagendamento no navegador vai precisar
  incluir PATCH em `allowedMethods`.
- SQLite e um banco em arquivo unico: adequado para o MVP e para demonstracao,
  mas nao recomendado para multiplas instancias do back-end rodando em
  paralelo (ver `DEPLOY.md`).
- O envio real de e-mail nao esta implementado; `dispararLembrete` grava o
  texto validado e marca o status como enviado, sem integrar a um provedor de
  e-mail de fato.


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
