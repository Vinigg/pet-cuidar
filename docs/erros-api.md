# Erros da API

Todo erro sai no formato padrão (`ApiErro`), tratado em
`exception/GlobalExceptionHandler`:

```json
{"status":502,"mensagem":"Nao foi possivel entregar o lembrete por e-mail apos todas as tentativas. O lembrete ficou registrado com status FALHOU.","timestamp":"2026-11-10T14:30:00"}
```

| HTTP | Quando | Exceção |
| --- | --- | --- |
| 400 | Dados inválidos: validação do corpo, parâmetro ausente ou de tipo errado, JSON malformado, regra de entrada | `MethodArgumentNotValidException`, `IllegalArgumentException` e afins |
| 404 | Recurso inexistente | `RecursoNaoEncontradoException` |
| 409 | Conflito de agenda, reserva concorrente, cadastro duplicado, transição de status inválida ou operação recusada pelo estado da consulta | `ConflitoHorarioException`, `OptimisticLockingFailureException`, `CadastroConflitanteException`, `TransicaoEstadoInvalidaException`, `OperacaoNaoPermitidaException` |
| 500 | Erro inesperado | qualquer outra `Exception` |
| 502 | Integração externa **sem fallback** falhou após todas as tentativas | `IntegracaoExternaIndisponivelException` |
| 504 | Integração externa **sem fallback** não respondeu no tempo limite | `IntegracaoExternaTimeoutException` |

## Regras

- **IA nunca gera 5xx.** Lembrete, resumo e predição têm fallback e respondem
  200 com o indicador (`usouFallback=true` ou `categoria=INDISPONIVEL`). Ver
  [resiliencia-ia.md](resiliencia-ia.md).
- **502/504 só para integração sem fallback.** Hoje o único caso é o envio
  manual de lembrete (`POST /consultas/{id}/lembrete`) quando o e-mail não é
  entregue depois de todas as tentativas: 504 se a última tentativa estourou o
  tempo limite e 502 nos demais casos.
- **500 não expõe detalhes.** A mensagem é sempre
  `Erro interno ao processar a requisicao. Tente novamente mais tarde.`, sem
  stack trace nem dados pessoais. O erro completo vai só para o log.
- Erros do próprio Spring MVC que já trazem status (405 método não suportado,
  404 rota inexistente, 415 tipo de conteúdo) mantêm esse status no formato
  padrão, em vez de virar 500.
- As mensagens de 502 e 504 também não contêm dados pessoais (e-mail do
  tutor, nomes).
