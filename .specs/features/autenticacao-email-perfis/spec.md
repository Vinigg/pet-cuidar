# Autenticação por e-mail e perfis

**Approved**: 2026-11-18 - user: "Aprovo a especificação"

## Problem Statement

O login recebe um perfil escolhido na tela e abre o painel sem validar a identidade. A API atual aceita chamadas diretas às operações da clínica.

## Goals

- Autenticar contas com um token temporário enviado por e-mail.
- Aplicar as mesmas permissões de perfil na navegação, nas rotas e na API.
- Associar os dados de tutores e veterinários à identidade autenticada.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Login por senha ou provedor externo | A entrada será feita por e-mail. |
| Gerenciamento de outros admins pelo painel | O primeiro admin será provisionado por configuração. |

## Assumptions & Open Questions

| Decisão | Comportamento | Racional | Confirmed? |
| --- | --- | --- | --- |
| Login | Solicitar e validar um token enviado por e-mail a cada nova sessão. | Fluxo solicitado. | y |
| Perfis | Aplicar a divisão Tutor, Veterinário, Colaborador e Admin confirmada pelo usuário. | Define as rotas e permissões. | y |
| Contas da clínica | Admin inicial por configuração. Admin associa e-mails ao cadastro de veterinários e cadastra colaboradores pelo painel. | Fluxo escolhido pelo usuário. | y |
| Autocadastro | Um e-mail verificado permite concluir o autocadastro exclusivamente como Tutor. | Fluxo escolhido pelo usuário. | y |
| Validade | Token de uso único com validade de 10 minutos. Sessão de até 8 horas, revogável ao sair. | Limita a reutilização de credenciais. | n |
| Reenvio e verificação | Intervalo de 60 segundos por e-mail. Até cinco erros por desafio. Limites adicionais por IP nas rotas públicas. | Considera tentativas automatizadas e reenvio acidental. | n |
| Dados anteriores | Um tutor existente entra pelo e-mail cadastrado. O admin associa veterinários existentes a um e-mail antes do primeiro acesso. E-mails ambíguos não conferem acesso. | Preserva os vínculos registrados no banco. | n |
| Escopo dos prontuários | O veterinário acessa o histórico dos pets associados às suas consultas e registra atendimentos nas suas próprias consultas. | Usa o vínculo já presente nas consultas. | n |
| Rotas proibidas | Visitante é encaminhado para entrada. Usuário com sessão recebe a indicação de acesso negado e um caminho para o painel. | Dá retorno sem exibir o conteúdo protegido. | n |

**Open questions:** none.

## User Stories

### P1: Entrar por e-mail

**User Story**: Como usuário cadastrado, quero confirmar meu e-mail para entrar no painel do meu perfil.

**Why P1**: A API precisa verificar a identidade antes de liberar operações.

**Acceptance Criteria**:
1. WHEN uma conta ativa solicita entrada THEN o servidor SHALL enviar um token temporário para seu e-mail pelo provedor configurado.
2. WHEN o token de entrada correto é verificado antes da expiração THEN o servidor SHALL criar uma sessão do perfil cadastrado.
3. WHEN o usuário sai THEN o servidor SHALL invalidar a sessão, inclusive em chamadas diretas posteriores.
4. WHILE uma sessão válida permanece ativa o front-end SHALL recuperar o perfil do servidor ao recarregar uma rota protegida.

**Independent Test**: Solicitar o token, validá-lo, recarregar o painel e confirmar que sair bloqueia a sessão anterior.

### P1: Navegar e operar conforme o perfil

**User Story**: Como usuário, quero ver meu painel e operar apenas os recursos permitidos pelo meu perfil.

**Why P1**: URLs diretas e chamadas à API precisam respeitar as mesmas permissões que a navegação.

**Acceptance Criteria**:
1. WHEN a sessão identifica um Tutor THEN o front-end SHALL mostrar apenas início, pets e consultas do Tutor, incluindo os fluxos de cadastro de pet e agendamento.
2. WHEN a sessão identifica um Veterinário THEN o front-end SHALL mostrar apenas histórico e registro de atendimentos.
3. WHEN a sessão identifica um Colaborador THEN o front-end SHALL mostrar apenas painel do dia, agendamento e cadastro de tutores e pets.
4. WHEN a sessão identifica um Admin THEN o front-end SHALL mostrar a gestão de veterinários e colaboradores.
5. WHEN um visitante abre uma rota protegida THEN o front-end SHALL encaminhá-lo à entrada antes de renderizar o painel.
6. WHEN uma sessão abre a rota de outro perfil THEN o front-end SHALL indicar acesso negado sem montar o painel.
7. IF uma operação protegida é chamada sem sessão válida THEN a API SHALL responder 401.
8. IF uma operação é chamada por um perfil sem permissão THEN a API SHALL responder 403.
9. WHILE um Tutor está autenticado as operações de pets e consultas SHALL usar os recursos do seu próprio cadastro, inclusive quando a requisição informa outro identificador.
10. WHILE um Veterinário está autenticado a API SHALL limitar o registro e a edição de atendimentos às suas consultas e o histórico aos pets associados a elas.

**Independent Test**: Entrar com os quatro perfis, comparar menus e URLs diretas, e tentar chamar operações de outro perfil e recursos de outro usuário.

### P1: Provisionar contas da clínica

**User Story**: Como admin, quero autorizar veterinários e colaboradores a entrarem com seus e-mails.

**Why P1**: Contas da clínica precisam de um vínculo confiável antes da verificação do e-mail.

**Acceptance Criteria**:
1. WHEN o servidor é configurado com o e-mail inicial de admin THEN a conta correspondente SHALL poder entrar como Admin após verificar o token.
2. WHEN um Admin cadastra ou associa o e-mail de um veterinário THEN o e-mail correspondente SHALL poder entrar apenas como Veterinário, vinculado ao cadastro profissional.
3. WHEN um Admin cadastra o e-mail de um colaborador THEN o e-mail correspondente SHALL poder entrar apenas como Colaborador.
4. IF o e-mail informado já está ligado a uma conta conflitante THEN o servidor SHALL rejeitar o cadastro com 409 sem trocar o perfil da conta existente.

**Independent Test**: Entrar como admin configurado, cadastrar as duas funções e verificar que cada conta entra no próprio painel.

### P1: Autocadastrar tutor

**User Story**: Como novo tutor, quero confirmar meu e-mail antes de criar minha conta e cadastrar meus pets.

**Why P1**: Preserva o autocadastro e confirma que o tutor controla o endereço antes de gravar os dados.

**Acceptance Criteria**:
1. IF o autocadastro é concluído sem prova válida do e-mail THEN a API SHALL rejeitar a gravação.
2. WHEN um novo tutor conclui o cadastro com prova válida THEN a API SHALL usar o e-mail verificado e criar a conta com perfil Tutor.
3. WHEN o cadastro é concluído THEN o front-end SHALL abrir o painel do tutor autenticado.

**Independent Test**: Percorrer o cadastro desde a tela de entrada e tentar concluir antes e depois de verificar o e-mail.

## Edge Cases

- IF um token expira, está incorreto, foi consumido ou foi substituído por reenvio THEN a API SHALL rejeitar a verificação sem iniciar sessão.
- WHEN solicitações concorrentes tentam consumir o mesmo token THEN apenas uma SHALL poder iniciar uma sessão.
- IF uma solicitação de entrada usa e-mail desconhecido THEN a API SHALL retornar o mesmo status e mensagem da solicitação para conta conhecida.
- IF o limite de tentativas ou de envio é atingido THEN a API SHALL limitar a operação sem ampliar a validade do token.
- IF o envio falha THEN o servidor SHALL descartar o desafio correspondente e permitir nova solicitação conforme os limites de envio.
- IF um cadastro existente não pode ser associado inequivocamente a um e-mail THEN o servidor SHALL recusar a autenticação desse cadastro.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| AUTH-01 | P1: Entrar por e-mail | Specify | Pending |
| AUTH-02 | P1: Navegar e operar conforme o perfil | Specify | Pending |
| AUTH-03 | P1: Provisionar contas da clínica | Specify | Pending |
| AUTH-04 | P1: Autocadastrar tutor | Specify | Pending |

## Sources

- Solicitação inicial e respostas registradas em [context.md](context.md).
- [Autorização por recurso e por requisição](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).
- [Verificação de e-mail](https://cheatsheetseries.owasp.org/cheatsheets/Email_Validation_and_Verification_Cheat_Sheet.html).

## Success Criteria

- Os quatro perfis entram com o código recebido e abrem apenas seu painel.
- Rotas diretas, chamadas à API e identificadores de terceiros respeitam as permissões da conta.
- O Admin provisiona contas da clínica e o Tutor conclui o autocadastro somente após verificar o e-mail.
