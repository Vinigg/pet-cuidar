# Contrato de dados

Schema oficial: `backend/src/main/resources/db/migration/V1__criar_schema.sql` (Flyway).
O Hibernate nao altera o banco (`ddl-auto=none`). Toda mudanca de tabela vira uma nova migration `V2__...`, `V3__...`.

## Relacionamentos
- Tutor 1:N Pet
- Pet 1:N Consulta
- Veterinario 1:N Consulta
- Pet 1:N HistoricoAtendimento (consulta_id opcional)
- Consulta 1:1 Lembrete (consulta_id unico)
- Consulta 1:1 PredicaoNoShow (consulta_id unico)

## Separacao IA x dado validado
| Tabela | Saida da IA / bruta | Valor validado / usado |
|---|---|---|
| lembretes | `texto_gerado_ia` (so auditoria) | `texto_validado` (o que vai ao tutor), `usou_fallback` |
| predicoes_no_show | `score_cru` (so auditoria) | `score_usado` (0 a 1, exibido), `usou_fallback` |
| historico_atendimentos | `resumo_ia` | `descricao` |

Regra para back-end e predicao: ler sempre `texto_validado` e `score_usado`. Os campos "cru" e `*_ia` nunca vao para tela ou para o tutor. `score_cru` e `score_usado` sao nulos quando o preditor esta indisponivel.

## Seed
`DataSeeder` (perfil diferente de `prod`) cria 3 veterinarios, 3 tutores, 4 pets, consultas passadas e futuras em varios status, historico, lembretes e predicoes. So roda com o banco vazio.

## Atencao
Quem ja tem `petcuidar.db` criado pela versao antiga precisa apagar o arquivo antes de subir (o schema agora vem do Flyway).
