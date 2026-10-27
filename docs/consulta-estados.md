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
ser definido pela clínica. Uma transição manual não cria histórico clínico,
envia/cancela e-mail nem agenda lembretes: essas ações continuam dependentes
das issues PC-027, PC-028 e PC-032. Predição ou IA nunca altera o estado.

O diagrama também menciona ações ao entrar em alguns estados, mas o backend
atual não implementa a automação de lembretes nem o registro de atendimento;
por isso, esta integração limita-se à validação e persistência do status.
