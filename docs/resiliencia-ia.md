# Lembretes com IA: provedor, guardrail, fallback e resiliência

`POST /consultas/{id}/lembrete` gera e registra o lembrete de uma consulta.
A resposta não muda conforme o caminho usado:

```json
{"status":"ENVIADO","textoEnviado":"Olá, Ana Souza! ...","usouFallback":false}
```

`usouFallback=true` indica que o texto veio do template determinístico. A
operação continua com HTTP 200: falha da IA nunca vira erro 5xx. Os únicos
erros possíveis são 404 (consulta inexistente) e 409 (consulta cancelada ou
consulta sem tutor, pet ou data/hora).

## Fluxo

```
ConsultaService.dispararLembrete
  └─ LembreteGeneratorService.gerar(consulta)
       1. DadosLembrete.de(consulta)      dados persistidos (fonte de verdade)
       2. LembreteTemplate.montar(dados)   valida obrigatórios e prepara o fallback
       3. circuito ABERTO?                 → template, sem chamar o provedor
       4. LembreteIaClient.gerar(dados)    com timeout ia.timeout-ms
            falha / timeout / resposta sem texto → template
       5. LembreteGuardrail.avaliar(...)  rejeitou → template
       6. aprovado                         → texto da IA
```

`Lembrete.textoGeradoIa` guarda a saída bruta do modelo e `textoValidado`
guarda o texto efetivamente enviado, para auditoria (PC-030).

## Provedor de LLM (PC-030)

A porta `LembreteIaClient` tem duas implementações, escolhidas por
`ia.llm.provider`:

| Valor | Classe | Uso |
| --- | --- | --- |
| `simulador` (padrão) | `LembreteIaSimulator` | Desenvolvimento e demonstração sem credenciais. **Não é integração com LLM.** |
| `anthropic` | `AnthropicLembreteIaClient` | Integração real pela API da Anthropic (Messages API), via SDK oficial `com.anthropic:anthropic-java` |

Por que a Anthropic: o projeto não definia provedor, e README e DEPLOY citavam
"Anthropic ou OpenAI" como exemplos. Foi usado o SDK oficial em Java, sem
cliente HTTP escrito à mão. A porta continua permitindo outro provedor sem
alterar breaker, guardrail ou template.

Detalhes da chamada:

- Modelo padrão `claude-opus-5-5`, configurável por `LLM_MODEL`.
  `LLM_EFFORT=low` (padrão) mantém a resposta curta e rápida dentro do timeout.
- Sem retentativas no SDK (`maxRetries=0`), e o timeout do SDK é o mesmo
  `ia.timeout-ms` (5 s). Repetições ficam a cargo do circuit breaker.
- O fallback do lado do servidor da Anthropic fica habilitado: em caso de
  recusa do modelo, a própria API tenta um modelo alternativo. Se ainda assim
  não houver texto, entra o template.
- O prompt envia só nome do tutor, nome do pet, nome do veterinário, data e
  horário. Nunca envia e-mail, telefone ou CPF.
- Resposta vazia, recusa (`stop_reason=refusal`) ou truncada (`max_tokens`)
  gera `RespostaIaInvalidaException` e é contada como resposta inválida.
  Erro HTTP, JSON malformado ou ausência de chave contam como falha.
- Os logs registram apenas modelo, tipo de erro e motivo da rejeição. Não
  registram chave, prompt, texto gerado nem nomes.

### Configuração local

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `LLM_PROVIDER` | `simulador` | `anthropic` liga a integração real |
| `LLM_API_KEY` | vazio | Chave da API da Anthropic. **Obrigatória para `anthropic`.** Nunca versionar. |
| `LLM_MODEL` | `claude-opus-5-5` | Modelo usado |
| `LLM_EFFORT` | `low` | `low`, `medium`, `high`, `xhigh` ou `max` |
| `LLM_MAX_TOKENS` | `2048` | Teto de tokens da resposta (inclui o raciocínio do modelo) |
| `LLM_BASE_URL` | `https://api.anthropic.com` | Endpoint da API |

```bash
# bash
export LLM_PROVIDER=anthropic
export LLM_API_KEY=<sua-chave>
cd backend && mvn spring-boot:run
```

```powershell
# PowerShell
$env:LLM_PROVIDER = "anthropic"; $env:LLM_API_KEY = "<sua-chave>"
cd backend; mvn spring-boot:run
```

Na inicialização o log mostra `Lembretes via LLM habilitados (provedor=anthropic, modelo=...)`.
Com `LLM_PROVIDER=anthropic` e sem chave, a aplicação sobe normalmente,
registra um aviso e todos os lembretes saem pelo template.

## Guardrail determinístico

`LembreteGuardrail` aprova o texto da IA somente se:

- não estiver vazio e tiver até 600 caracteres;
- contiver, exatamente, o nome do tutor, o nome do pet, a data `dd/MM/yyyy`
  e o horário `HH:mm` persistidos;
- não contiver nenhum dígito além desses dados (isso barra outras datas,
  horários, telefones, valores e prazos inventados);
- não contiver link, e-mail ou `@`;
- não citar "Dr./Dra." com nome diferente do veterinário da consulta;
- não contiver placeholders (`{...}`, `[...]`, `<...>`, `null`, `undefined`).

O motivo da rejeição (ex.: `INFORMACAO_NAO_FORNECIDA`) aparece no log sem
dados pessoais. **Limite:** afirmações em texto livre sem números (ex.:
"traga a carteira de vacinação") não são detectáveis por regra; o prompt
proíbe esse conteúdo.

## Template de fallback (PC-032)

`LembreteTemplate` monta, com os dados persistidos:

> Olá, Ana Souza! Lembramos que Rex tem consulta agendada com Dr. Bruno
> Salgado em 24/11/2026 às 14:30. Contamos com a sua presença! Equipe Pet Cuidar.

- Nome do tutor, nome do pet e data/hora são obrigatórios. Se algum faltar,
  a operação responde 409 e a IA não é chamada.
- O veterinário e a assinatura (`lembrete.nome-clinica`) são omitidos quando
  vazios, nunca preenchidos com `null`.
- Nomes são normalizados (espaços extras removidos).
- O texto do template sempre passa pelo guardrail (coberto por teste).

## Circuit breaker, timeout e telemetria (PC-038)

`LembreteGeneratorService` mantém o circuit breaker em memória por instância,
sem biblioteca adicional. Os limites são configuráveis por `ia.timeout-ms`,
`ia.circuit-breaker.max-falhas` e `ia.circuit-breaker.reset-ms`.

O estado é FECHADO na operação normal. Depois do limite de falhas, fica
ABERTO e as chamadas seguintes vão direto para o fallback, sem chamar o
provedor. Passado o intervalo, só uma chamada de prova entra em TESTE. Uma
resposta aprovada pelo guardrail fecha o circuito e zera a sequência; falha,
timeout, resposta sem texto ou texto rejeitado reabrem o circuito.

O executor aceita no máximo quatro tarefas simultâneas, com threads daemon.
Cada chamada usa o timeout configurado e cancela o `Future` quando ele
estoura; o executor é encerrado junto com o serviço. Sob saturação, novas
tarefas são rejeitadas e resolvidas pelo fallback.

Falhas, timeouts, fallbacks, respostas inválidas e sucessos são contados em
`LembreteGeneratorService.Metricas` (`obterMetricas`). A telemetria é local à
instância e não persiste entre reinicializações.

## Testes

Nenhum teste chama o provedor real. `AnthropicLembreteIaClientTest` usa o SDK
verdadeiro contra um servidor HTTP local que imita a API. Assim ficam
cobertos a serialização, os cabeçalhos e a leitura da resposta (válida,
vazia, recusa, truncada, JSON malformado, HTTP 500/503 e timeout), além da
integração com timeout, circuit breaker, guardrail e fallback.
