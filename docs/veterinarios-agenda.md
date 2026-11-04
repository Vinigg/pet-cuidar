# Cadastro e agenda de veterinarios

## Contrato HTTP

| Metodo | Endpoint | Resultado |
|---|---|---|
| POST | `/veterinarios` | Cria profissional e escala inicial em uma transacao; 201 |
| GET | `/veterinarios?ativo=true` | Lista ativos, ordenados por nome; padrao `true` |
| GET | `/veterinarios?ativo=false` | Lista inativos para manutencao |
| GET | `/veterinarios/{id}` | Dados cadastrais; 200 |
| PUT | `/veterinarios/{id}` | Substitui nome, especialidade e ativo; 200 |
| GET | `/veterinarios/{id}/horarios-atendimento` | Escala semanal ordenada por dia e inicio |
| PUT | `/veterinarios/{id}/horarios-atendimento` | Substitui toda a escala em uma transacao; 200 |
| GET | `/veterinarios/{id}/disponibilidade?data=AAAA-MM-DD` | Grade calculada para a data |
| POST | `/veterinarios/{id}/bloqueios` | Cria ausencia, feriado ou ferias; 201 |
| GET | `/veterinarios/{id}/bloqueios?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` | Bloqueios que intersectam as datas, ambas inclusivas; janela maxima de um ano |
| DELETE | `/veterinarios/{id}/bloqueios/{bloqueioId}` | Remove bloqueio do profissional indicado; 204 |

### Cadastro com escala

```json
{
  "nome": "Dra. Ana Souza",
  "especialidade": "Clinica geral",
  "horarios": [
    {"diaSemana": "MONDAY", "horaInicio": "08:00", "horaFim": "12:00"},
    {"diaSemana": "MONDAY", "horaInicio": "14:00", "horaFim": "18:00"},
    {"diaSemana": "WEDNESDAY", "horaInicio": "09:00", "horaFim": "13:00"}
  ]
}
```

Resposta: `{"id":3,"nome":"Dra. Ana Souza","especialidade":"Clinica geral","ativo":true}`.
O id e gerado pelo banco. Nome e obrigatorio; nome e especialidade aceitam ate
255 caracteres. A escala inicial e opcional: omitir `horarios`, enviar `null`
ou `[]` cria o profissional **sem atendimento**, nunca com uma grade implicita.
Nao ha unicidade por nome: profissionais diferentes podem ter o mesmo nome.

Dias: `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`, `SUNDAY`.
Na edicao da escala, o corpo e `{"horarios":[...]}`; `[]` remove os periodos,
mas omitir a lista ou enviar elementos nulos e invalido. Limite de 100 periodos.

### Edicao e inativacao

```json
{"nome":"Dra. Ana Souza","especialidade":"Clinica geral","ativo":false}
```

`ativo` e obrigatorio no PUT. A inativacao preserva cadastro, escala e historico,
retira o profissional da listagem padrao e impede novas reservas. Nao existe
exclusao fisica de veterinarios pela API.

### Bloqueio por data

```json
{"inicio":"2027-01-11T00:00:00","fim":"2027-01-12T00:00:00","motivo":"Feriado"}
```

O inicio e inclusivo e o fim exclusivo. Para um dia inteiro, use meia-noite
ate a meia-noite seguinte; ferias podem abranger varios dias. Motivo e opcional,
com ate 255 caracteres. Bloqueios podem iniciar fora da grade: `08:15-08:45`
impede consultas de `08:00-08:30` e `08:30-09:00`. Nao sao permitidos
bloqueios sobrepostos nem periodos inteiramente passados. Remover um bloqueio
nao remove consultas nem modifica a escala.

## Regras de disponibilidade

- Consultas duram 30 minutos, com inicio em `:00` ou `:30`, sem segundos.
- A consulta inteira deve caber em um periodo; o fim do periodo nao e um inicio disponivel.
- Periodos semanais nao podem se sobrepor nem atravessar a meia-noite. Periodos contiguos sao aceitos.
- Dias sem escala, profissionais inativos e datas passadas retornam `[]`.
- Para hoje, somente horarios ainda nao passados aparecem.
- Horarios ocupados ou bloqueados continuam na grade, com `disponivel:false`.
- Consultas canceladas e marcadas como nao compareceu liberam o horario.
- Criacao e reagendamento revalidam a escala, os bloqueios e a ocupacao no servidor.
- Nao se pode remover da escala uma consulta futura ainda reservada, inativar seu
  profissional ou criar um bloqueio sobre ela. Reagende ou cancele antes; nada e movido automaticamente.
- Alteracoes de escala/bloqueio/cadastro usam a versao do veterinario, assim como
  reservas, para que escritas concorrentes nao confirmem silenciosamente regras incompativeis.

Datas e horas sao locais da clinica, sem offset, no fuso `AGENDA_TIME_ZONE`
(padrao `America/Recife`). O frontend deve enviar o horario local selecionado,
sem converter para UTC. Mantenha o fuso da JVM alinhado ao da clinica, pois os
fluxos legados de status e lembretes tambem usam o relogio do servidor.

Erros usam o envelope existente `ApiErro`: `400` para dados/horarios invalidos,
`404` para recurso inexistente e `409` para conflito, inclusive concorrencia.
O frontend deve atualizar a disponibilidade apos um conflito.

## Banco e migracao

Flyway executa as migracoes em `backend/src/main/resources/db/migration` antes
do Hibernate. Bancos legados sem historico recebem baseline na versao 0.
V1 reconhece/cria a tabela de veterinarios; V2 adiciona `ativo` e a escala;
V3 cria os bloqueios; V4 completa as demais tabelas e indices com
`IF NOT EXISTS`, preservando tabelas ja existentes. O Hibernate usa
`ddl-auto=none`; nao e responsavel pela criacao do schema.

A antiga `V1__criar_schema.sql`, que colidia com `V1__base_veterinarios.sql`,
foi substituida por `V4__completar_schema.sql`. As migrations V1 da base de
veterinarios, V2 e V3 foram preservadas. Se um ambiente ja registrou a outra
V1 (`criar_schema`) no historico, ele precisa de conciliacao especifica antes
da atualizacao. Nao execute `repair` nem apague o historico automaticamente.

Para preservar o comportamento anterior, somente os veterinarios ja existentes
recebem atendimento explicito de 08:00 a 18:00 nos sete dias. Os dois exemplos
de um banco novo tambem recebem essa escala pelo `DataSeeder`. A recepcao deve
revisar esses horarios, principalmente fins de semana. Reiniciar nao repovoa
escalas vazias de profissionais existentes. As migracoes sao aplicadas uma unica vez.

Antes de atualizar um ambiente com dados reais, pare a aplicacao e faca backup
do arquivo SQLite. Nao remova `flyway_schema_history` nem altere migracoes ja
aplicadas. A migracao foi testada em SQLite isolado; a base local do usuario
nao e alterada pelos testes.

## Seguranca e verificacao

No frontend, a aba **Veterinarios** cadastra nome, especialidade e escala semanal
em um unico POST. O formulario exige ao menos um dia, permite adicionar/remover
periodos, valida a grade e sobreposicoes e preserva os dados em falhas da API.
A opcao **Atualizar** carrega os dados e a escala de um profissional ativo ou
inativo. **Salvar dados** atualiza nome, especialidade e situacao pelo PUT de
cadastro; **Salvar horarios** substitui a escala pelo PUT de horarios. As duas
operacoes sao independentes, nao uma transacao conjunta. A edicao permite
esvaziar a escala, mas o servidor recusa alteracoes que afetem consultas futuras.
Erros preservam os campos, inclusive em conflitos 409. Durante o salvamento,
a troca de profissional e de modo fica bloqueada. Bloqueios por data ainda
nao possuem tela de manutencao.

O projeto ainda nao implementa autenticacao/perfis. Estes endpoints **nao estao
restritos a recepcao**. Antes de disponibilizar em producao, integrar autenticacao
e autorizar manutencao de profissionais e agenda apenas ao perfil responsavel.
CORS e ocultar botoes no frontend nao substituem autorizacao.

Exemplos executaveis: [veterinarios-agenda.http](../backend/requests/veterinarios-agenda.http).
Testes: `mvn -f backend/pom.xml test`. Os testes de agenda cobrem HTTP, SQLite,
migracao legada/idempotencia, intervalos, inativacao, bloqueios e preservacao
de consultas. Nao ha novo teste de carga com requisicoes simultaneas;
o mecanismo otimista existente foi mantido.