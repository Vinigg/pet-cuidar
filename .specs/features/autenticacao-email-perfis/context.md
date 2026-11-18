# Autenticação por e-mail e perfis

**Gathered:** 2026-11-18

## Feature Boundary

Autenticar pelo e-mail e restringir rotas, menus e operações da API aos perfis Tutor, Veterinário, Colaborador e Admin.

## Implementation Decisions

### Entrada

- Usuário: “Quero implementar no backend autenticação por token, sempre que um usuário quiser entrar no app um token é enviado para o email e verificado para o login”.

### Divisão de acesso

- Usuário: “Sim, manter a divisão proposta”.
- Tutor: área do tutor, pets e consultas próprios.
- Veterinário: histórico do pet e registro de atendimentos.
- Colaborador: painel do dia, agendamento e cadastro de tutores e pets.
- Admin: gestão de veterinários.

### Cadastro da clínica

- Usuário: “Admin inicial por configuração, com cadastro de veterinários e colaboradores pelo painel”.

### Autocadastro de tutor

- Usuário: “Sim, manter o autocadastro com verificação”.
