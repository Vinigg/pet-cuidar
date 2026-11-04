# Ciclo de vida da consulta

As transições são validadas por `ConsultaStateMachine` e aplicadas por
`ConsultaService.atualizarStatus`. Use `PATCH /consultas/{id}/status` com
`{"status":"CONFIRMADA"}` (ou outro estado permitido).

| Origem | Destino | Regra |
| --- | --- | --- |
| AGENDADA | CONFIRMADA | Confirmação humana |
| AGENDADA | REALIZADA | Atendimento registrado, inclusive sem confirmação prévia |
| CONFIRMADA | REALIZADA | Atendimento registrado |
| AGENDADA ou CONFIRMADA | CANCELADA | Somente antes de `dataHora` |
| AGENDADA ou CONFIRMADA | NAO_COMPARECEU | Somente após `dataHora` mais a tolerância configurada |

REALIZADA, CANCELADA e NAO_COMPARECEU são finais. Transições não descritas
são recusadas com HTTP 409; consulta inexistente retorna HTTP 404. O parâmetro
`consulta.tolerancia-nao-comparecimento-minutos` assume zero por padrão e pode
ser definido pela clínica. Uma transição manual não cria histórico clínico
nem envia ou cancela e-mails. Predição ou IA nunca altera o estado.

## Atualização de status pela recepção (PC-027)

`PATCH /consultas/{id}/status`

```http
PATCH /consultas/1/status
Content-Type: application/json

{"status": "CONFIRMADA"}
```

| Situação | HTTP |
| --- | --- |
| Transição válida: status gravado e consulta devolvida | 200 |
| Corpo ausente/malformado, `status` ausente ou valor fora do enum | 400 |
| Consulta inexistente | 404 |
| Transição não permitida (ex.: REALIZADA → CONFIRMADA) | 409 |

Resposta 200:

```json
{"id":1,"petNome":"Rex","tutorNome":"Ana Souza","veterinarioNome":"Dr. Bruno Salgado",
 "dataHora":"2026-11-23T10:00:00","status":"CONFIRMADA"}
```

Erros seguem o formato padrão da PC-010:
`{"status":409,"mensagem":"Transicao de status nao permitida.","timestamp":"..."}`.

Fluxo: localizar consulta → `ConsultaStateMachine.transicionar` → persistir
→ devolver. Uma transição recusada não altera o registro; o campo `status` da
entidade não possui setter público. O preditor de no-show lê o status gravado,
então NAO_COMPARECEU e REALIZADA passam a alimentar o histórico do pet.

**Limitação:** o projeto ainda não tem autenticação nem perfis (ver README).
O endpoint é *destinado* à recepção, mas qualquer cliente da API consegue
chamá-lo até que o controle de acesso seja implementado.

## Cancelamento (PC-028)

O cancelamento é a transição para CANCELADA no endpoint acima. Não existe um
endpoint separado, para não duplicar a regra:

```http
PATCH /consultas/2/status
Content-Type: application/json

{"status": "CANCELADA"}
```

- Permitido somente para AGENDADA ou CONFIRMADA e antes de `dataHora`
  (regra da PC-026). Nenhum outro prazo foi definido pelo projeto.
- O horário volta a ficar livre na disponibilidade e pode receber outro
  agendamento.
- `POST /consultas/{id}/lembrete` passa a responder 409 para a consulta
  cancelada (PC-028 e PC-029: consultas canceladas não recebem lembrete).

## Reagendamento (PC-028)

`PATCH /consultas/{id}/reagendamento`

```http
PATCH /consultas/1/reagendamento
Content-Type: application/json

{"dataHora": "2026-11-24T14:30:00"}
```

| Situação | HTTP |
| --- | --- |
| Reagendado: devolve a consulta com a nova `dataHora` | 200 |
| `dataHora` ausente/malformada, fora da grade de 30 min, fora de 08:00–17:30, no passado ou igual ao horário atual | 400 |
| Consulta inexistente | 404 |
| Horário ocupado para o mesmo veterinário, consulta em estado final, horário original já passado ou reserva concorrente | 409 |

Fluxo (`ConsultaService.reagendar`, transacional):

1. Valida a nova data com as mesmas regras de horário do agendamento.
2. Localiza a consulta (404) e chama `ConsultaStateMachine.validarReagendamento`.
3. Obtém o veterinário com o mesmo bloqueio otimista do agendamento
   (`buscarParaAgendamento`): duas reservas simultâneas para o mesmo
   veterinário resultam em 409, nunca em horário duplicado.
4. Verifica conflito com a mesma regra usada na criação (`verificarConflito`):
   consultas CANCELADA ou NAO_COMPARECEU não ocupam o horário.
5. Só então altera `dataHora` e persiste. Qualquer exceção desfaz a transação
   e a consulta original permanece intacta.
6. Se já havia lembrete, ele volta a `PENDENTE` (texto e data de envio
   limpos), porque citava o horário antigo. O próximo envio usa o novo horário.

Decisões tomadas onde a documentação era omissa:

- **Mesma consulta, sem cancelar e recriar.** O critério "falha preserva
  consulta original" e a ausência de um estado REAGENDADA indicam alteração no
  próprio registro. Id, pet, veterinário e status são preservados.
- **Status mantido.** Uma consulta CONFIRMADA continua CONFIRMADA. A máquina
  de estados não permite voltar para AGENDADA e não há regra que peça nova
  confirmação.
- **Somente antes do horário original e em estado não final**, como no
  cancelamento. Depois do horário, a consulta é candidata a NAO_COMPARECEU, e
  mover a data apagaria esse desfecho do histórico de no-show.
- **Troca de veterinário não faz parte do reagendamento**: só a data/hora muda.
