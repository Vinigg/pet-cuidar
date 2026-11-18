# Autenticação por e-mail e perfis — Tasks

**Design**: `.specs/features/autenticacao-email-perfis/design.md`
**Status**: Done
**Approved**: 2026-11-18 - user: "Aprovo o plano e a implementação"
**Build**: `cd backend && mvn --batch-mode verify; cd frontend && npm run build`
**Run api**: `cd backend && mvn --batch-mode -Dtest=AutenticacaoPerfisIntegrationTest,DesafioEmailIntegrationTest,MigracaoContasTest,LimiteProxyIntegrationTest test`
**Run front**: `cd frontend && npm test -- --watchAll=false --runInBand --verbose --runTestsByPath src/App.test.jsx`

## Test Coverage Matrix

> Referências do projeto: `.github/workflows/ci.yml`, `backend/pom.xml` e `frontend/package.json`. Cobertura por critério, conforme os padrões MockMvc, JUnit e Jest existentes.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| API e persistência | integration | Cenários HTTP por perfil, vínculo, sessão e casos de borda. | `backend/src/test/java/com/petcuidar/` | `mvn --batch-mode verify` |
| React | integration | Formulários, URLs diretas, recuperação de sessão e menus. | `frontend/src/*.test.jsx` | `npm test -- --watchAll=false --runInBand` |
| Configuração documentada | none | Build e instruções verificáveis. | `DEPLOY.md` | `npm run build` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Full | API ou React | `cd backend && mvn --batch-mode verify` e `cd frontend && npm test -- --watchAll=false --runInBand` |
| Build | Final da entrega | Comandos Full e `cd frontend && npm run build` |

## Execution Plan

### Phase 1: Autenticação no servidor

```text
T1 → T2 → T3 → T4 → T5 → T6
```

### Phase 2: Uso pelo navegador

```text
T6 → T7 → T8
```

## Task Breakdown

### T1: Persistir contas e vínculos

**What**: Persistir contas e vínculos.
**Where**: `backend/src/main/resources/db/migration/`
**Depends on**: None
**Requirement**: AUTH-03

**Done when**:

- [x] O Flyway migra tutores sem duplicar e-mails normalizados, com restrições para os vínculos.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T2: Emitir e validar desafios de e-mail

**What**: Emitir e validar desafios de e-mail.
**Where**: `backend/src/main/java/com/petcuidar/auth/`
**Depends on**: T1
**Requirement**: AUTH-01

**Done when**:

- [x] O provedor recebe o código e a API valida expiração, uso único, concorrência, reenvio e limites.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T3: Criar, recuperar e revogar sessões

**What**: Criar, recuperar e revogar sessões.
**Where**: `backend/src/main/java/com/petcuidar/auth/`
**Depends on**: T2
**Requirement**: AUTH-01

**Done when**:

- [x] Um código de entrada válido inicia sessão no perfil registrado. Sair invalida a credencial.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T4: Aplicar autorização nas operações existentes

**What**: Aplicar autorização nas operações existentes.
**Where**: `backend/src/main/java/com/petcuidar/controller/`
**Depends on**: T3
**Requirement**: AUTH-02

**Done when**:

- [x] A API rejeita chamadas sem sessão, perfis sem autorização e recursos fora do vínculo da conta.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T5: Provisionar contas da clínica

**What**: Provisionar contas da clínica.
**Where**: `backend/src/main/java/com/petcuidar/auth/`
**Depends on**: T4
**Requirement**: AUTH-03

**Done when**:

- [x] O admin inicial entra por configuração e provisiona veterinários e colaboradores sem colisão de contas.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T6: Concluir cadastro de tutor com e-mail verificado

**What**: Concluir cadastro de tutor com e-mail verificado.
**Where**: `backend/src/main/java/com/petcuidar/auth/`
**Depends on**: T5
**Requirement**: AUTH-04

**Done when**:

- [x] A prova de cadastro é validada e consumida na criação transacional da conta e dos pets.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T7: Conectar o acesso do React aos quatro painéis

**What**: Conectar o acesso do React aos quatro painéis.
**Where**: `frontend/src/`
**Depends on**: T6
**Requirement**: AUTH-01, AUTH-02, AUTH-03, AUTH-04

**Done when**:

- [x] Login e autocadastro abrem o painel correto. Rotas diretas são protegidas. Admin provisiona a equipe, e Tutor usa dados da própria conta.
- [x] A verificação Full passa.

**Tests**: integration
**Gate**: full

### T8: Documentar configuração de implantação

**What**: Documentar configuração de implantação.
**Where**: `DEPLOY.md`
**Depends on**: T7
**Requirement**: AUTH-01

**Done when**:

- [x] As instruções cobrem administrador inicial, envio por Resend e proxy de mesma origem com HTTPS.
- [x] A verificação Build passa.

**Tests**: none
**Gate**: build

## Checks

Os testes associados a cada critério identificam seu resultado com a linha abaixo após as asserções.

**C1** - servidor envia o código da conta ativa (AUTH-01, AC 1)
Proof: run api | PASS C1 servidor envia o código da conta ativa

**C2** - código correto inicia sessão do perfil cadastrado (AUTH-01, AC 2)
Proof: run api | PASS C2 código correto inicia sessão do perfil cadastrado

**C3** - sair invalida a sessão no servidor (AUTH-01, AC 3)
Proof: run api | PASS C3 sair invalida a sessão no servidor

**C4** - recarregar restaura o perfil a partir do servidor (AUTH-01, AC 4)
Proof: run front | √ C4 recarregar restaura o perfil a partir do servidor

**C5** - Tutor vê somente sua navegação (AUTH-02, AC 1)
Proof: run front | √ C5 Tutor vê somente sua navegação

**C6** - Veterinário vê somente sua navegação (AUTH-02, AC 2)
Proof: run front | √ C6 Veterinário vê somente sua navegação

**C7** - Colaborador vê somente sua navegação (AUTH-02, AC 3)
Proof: run front | √ C7 Colaborador vê somente sua navegação

**C8** - Admin vê somente a gestão de equipe (AUTH-02, AC 4)
Proof: run front | √ C8 Admin vê somente a gestão de equipe

**C9** - visitante é enviado à entrada (AUTH-02, AC 5)
Proof: run front | √ C9 visitante é enviado à entrada

**C10** - URL de outro perfil mostra acesso negado (AUTH-02, AC 6)
Proof: run front | √ C10 URL de outro perfil mostra acesso negado

**C11** - API retorna 401 para sessão ausente ou inválida (AUTH-02, AC 7)
Proof: run api | PASS C11 API retorna 401 para sessão ausente ou inválida

**C12** - API retorna 403 para perfil incorreto (AUTH-02, AC 8)
Proof: run api | PASS C12 API retorna 403 para perfil incorreto

**C13** - Tutor só manipula pets e consultas da própria conta (AUTH-02, AC 9)
Proof: run api | PASS C13 Tutor só manipula pets e consultas da própria conta

**C14** - Veterinário só manipula atendimentos vinculados (AUTH-02, AC 10)
Proof: run api | PASS C14 Veterinário só manipula atendimentos vinculados

**C15** - admin inicial entra pelo e-mail configurado (AUTH-03, AC 1)
Proof: run api | PASS C15 admin inicial entra pelo e-mail configurado

**C16** - admin provisiona veterinário vinculado (AUTH-03, AC 2)
Proof: run api | PASS C16 admin provisiona veterinário vinculado

**C17** - admin provisiona colaborador (AUTH-03, AC 3)
Proof: run api | PASS C17 admin provisiona colaborador

**C18** - e-mail conflitante retorna 409 (AUTH-03, AC 4)
Proof: run api | PASS C18 e-mail conflitante retorna 409

**C19** - autocadastro sem prova não grava dados (AUTH-04, AC 1)
Proof: run api | PASS C19 autocadastro sem prova não grava dados

**C20** - autocadastro usa e-mail verificado e perfil Tutor (AUTH-04, AC 2)
Proof: run api | PASS C20 autocadastro usa e-mail verificado e perfil Tutor

**C21** - conclusão do autocadastro abre painel do Tutor (AUTH-04, AC 3)
Proof: run front | √ C21 conclusão do autocadastro abre painel do Tutor

**C22** - código incorreto expirado consumido ou substituído é rejeitado (Casos de borda, AC 1)
Proof: run api | PASS C22 código incorreto expirado consumido ou substituído é rejeitado

**C23** - somente um consumo concorrente tem sucesso (Casos de borda, AC 2)
Proof: run api | PASS C23 somente um consumo concorrente tem sucesso

**C24** - e-mail desconhecido recebe resposta genérica (Casos de borda, AC 3)
Proof: run api | PASS C24 e-mail desconhecido recebe resposta genérica

**C25** - limites de envio e verificação são aplicados (Casos de borda, AC 4)
Proof: run api | PASS C25 limites de envio e verificação são aplicados
Proof: run api | PASS C25 clientes do mesmo proxy têm cotas distintas

**C26** - falha de envio descarta o desafio (Casos de borda, AC 5)
Proof: run api | PASS C26 falha de envio descarta o desafio

**C27** - e-mail ambíguo não permite autenticação (Casos de borda, AC 6)
Proof: run api | PASS C27 e-mail ambíguo não permite autenticação
Proof: run api | PASS C27 migração preserva e-mails legados inequívocos

## Handoff

**Settled mid-build:** none
**Dispatched:** Verifier at `5a3be15`, returned
**Walked:** Chrome headless, API Spring Boot, banco SQLite descartável e servidor HTTP local simulando o Resend. Admin entrou por código, cadastrou colaborador, vinculou veterinário, recarregou o painel e saiu. Colaborador abriu o painel do dia e recebeu acesso negado em rota clínica direta e na API clínica. Veterinário abriu histórico e atendimento. Tutor verificou o e-mail, cadastrou a conta e o pet pela interface, acessou suas consultas e teve a sessão revogada ao sair.
