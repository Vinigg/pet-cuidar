# Resiliência e telemetria da geração de lembretes

`LembreteGeneratorService` mantém o circuit breaker em memória por instância,
sem adicionar uma segunda biblioteca. O limite é configurável por
`ia.timeout-ms`, `ia.circuit-breaker.max-falhas` e
`ia.circuit-breaker.reset-ms`.

O estado é FECHADO durante a operação normal; depois do limite de falhas fica
ABERTO e novas chamadas usam imediatamente o fallback determinístico. Após o
intervalo, apenas uma chamada de prova pode entrar em TESTE. Uma resposta que
passa pelo guardrail fecha o circuito e zera a sequência; falha ou resposta
rejeitada reabre o circuito. O guardrail exige nome do tutor, nome do pet e
data/hora.

O executor possui no máximo quatro tarefas simultâneas e threads daemon. Cada
chamada usa o timeout configurado e cancela o Future em timeout; o executor é
encerrado junto com o serviço. Sob saturação, novas tarefas são rejeitadas e
resolvidas pelo fallback, sem criar threads sem limite.

Falhas, timeouts, fallbacks, respostas inválidas e sucessos são contados em
`LembreteGeneratorService.Metricas` (`obterMetricas`) e eventos do breaker e
fallback são registrados em log sem nomes, e-mails ou dados de pets. A
telemetria é local à instância e não persiste entre reinicializações. O
cliente de IA é uma porta (`LembreteIaClient`) com simulador local; a operação
continua sem credenciais ou chamadas externas. Falhas do gerador continuam
resolvidas antes do handler HTTP pelo template, mantendo o contrato atual.
