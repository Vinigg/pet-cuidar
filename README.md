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

As chamadas a um provedor de IA (geracao de lembrete e resumo de historico)
estao **simuladas localmente** em `service/ia/*.java`, para que o projeto rode
sem exigir chaves de API. A logica de timeout, guardrail de validacao,
fallback deterministico e circuit breaker esta implementada de verdade — so a
chamada externa ao modelo de linguagem e um stub, comentado no codigo indicando
onde entraria a chamada real (ex.: API da Anthropic ou da OpenAI via
RestTemplate/WebClient).

## Endpoints principais da API

| Metodo | Endpoint | Descricao |
|---|---|---|
| POST | `/tutores` | Cadastra tutor e pet(s) |
| GET | `/veterinarios` | Lista veterinarios |
| GET | `/veterinarios/{id}/disponibilidade?data=` | Horarios livres/ocupados no dia |
| POST | `/consultas` | Cria agendamento (valida conflito de horario) |
| PATCH | `/consultas/{id}/status` | Transiciona status da consulta conforme a máquina de estados |
| GET | `/consultas?data=` | Lista consultas do dia (painel da recepcionista) |
| GET | `/consultas/{id}/predicao-no-show` | Score de risco de falta + motivo |
| POST | `/consultas/{id}/lembrete` | Gera e "envia" o lembrete (IA + fallback) |
| GET | `/pets/{id}/historico` | Historico completo do pet |
| GET | `/pets/{id}/historico/resumo` | Resumo gerado por IA + link do historico |

## Limitacoes conhecidas do MVP

- Sem autenticacao/login implementados (o documento de arquitetura previa JWT
  para recepcionista/veterinario — fica como proxima etapa).
- SQLite e um banco em arquivo unico: adequado para o MVP e para demonstracao,
  mas nao recomendado para multiplas instancias do back-end rodando em
  paralelo (ver `DEPLOY.md`).
- O envio real de e-mail nao esta implementado; `dispararLembrete` grava o
  texto validado e marca o status como enviado, sem integrar a um provedor de
  e-mail de fato.


## Validando o cadastro de tutor e pets

Em `backend/`, execute `mvn test`. Os testes de cadastro usam um arquivo
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
