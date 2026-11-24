# IA: provedor, guardrail, fallback e resiliência

Três integrações usam serviços externos com fallback determinístico:

| Integração | Service | Fallback | Indicador na resposta |
| --- | --- | --- | --- |
| Lembrete (US3.1) | `LembreteGeneratorService` | Template (`LembreteTemplate`) | `usouFallback=true` |
| Resumo do histórico (US5.1) | `ResumoHistoricoService` | Resumo determinístico | `usouFallback=true` |
| Predição de no-show (US4.1) | `NoShowPredictorService` | "Score indisponível" | `score=null`, `categoria=INDISPONIVEL` |

**Falha de IA nunca vira erro 5xx.** Erro do provedor, timeout, resposta
inválida ou circuito aberto: a operação responde HTTP 200 com o indicador de
fallback. A tabela completa de códigos HTTP está em [erros-api.md](erros-api.md).

## Lembrete: `POST /consultas/{id}/lembrete`

Gera, registra e envia o lembrete de uma consulta:

```json
{"status":"ENVIADO","textoEnviado":"Olá, Ana Souza! ...","usouFallback":false}
```

| Situação | HTTP |
| --- | --- |
| Lembrete enviado (texto da IA ou do template) | 200 |
| Consulta inexistente | 404 |
| Consulta cancelada, ou sem tutor, pet ou data/hora | 409 |
| E-mail não entregue após todas as tentativas | 502 |
| Provedor de e-mail sem resposta no tempo limite em todas as tentativas | 504 |

502 e 504 vêm só da **entrega do e-mail**, que não tem fallback. O lembrete
continua gravado com status `FALHOU` para auditoria, e chamadas repetidas
respondem o mesmo código sem reenviar. O job agendado usa o mesmo fluxo de
entrega, mas nunca lança erro: só registra `FALHOU`.

### Fluxo

```
ConsultaService.dispararLembrete
  └─ LembreteDeliveryService.dispararManual
       └─ LembreteGeneratorService.gerar(consulta)
            1. DadosLembrete.de(consulta)      dados persistidos (fonte de verdade)
            2. LembreteTemplate.montar(dados)   valida obrigatórios e prepara o fallback
            3. circuito ABERTO?                 → template, sem chamar o provedor
            4. LembreteIaClient.gerar(dados)    com timeout ia.lembrete.timeout-ms
                 falha / timeout / resposta sem texto → template
            5. LembreteGuardrail.avaliar(...)  rejeitou → template
            6. aprovado                         → texto da IA
       └─ EmailSender.enviar(...)              até 3 tentativas com backoff
            FALHOU por timeout → 504; FALHOU por outro motivo → 502
```

`Lembrete.textoGeradoIa` guarda a saída bruta do modelo e `textoValidado`
guarda o texto efetivamente enviado, para auditoria (PC-030).

## Provedor de IA

Exatamente um `LembreteIaClient` e um `ResumoIaClient` são registrados, por
`config/IaClientConfig`, para qualquer combinação de propriedades. O provedor
é escolhido por `ia.provider` (variável `LLM_PROVIDER`):

| Valor | Lembrete | Resumo | Uso |
| --- | --- | --- | --- |
| `simulador` (padrão) | `LembreteIaSimulator` | `LembreteIaSimulator` | Desenvolvimento e demonstração sem credenciais. **Não é integração com LLM.** |
| `openai` | `OpenAiIaClient` | `OpenAiIaClient` | API compatível com `/chat/completions` |
| `llm-externo` | `AnthropicLembreteIaClient` | simulador | LLM externo via SDK oficial em Java. Atende só lembretes. |

Regras de seleção:

- Valor desconhecido impede a inicialização com uma mensagem que lista os
  valores aceitos (`simulador`, `openai`, `llm-externo`).
- `simulator` (grafia antiga) é aceito como `simulador`.
- `IA_PROVIDER` continua aceito quando `LLM_PROVIDER` não está definido.
- A propriedade antiga `ia.llm.provider` só é considerada quando `ia.provider`
  é o simulador. Se as duas apontarem provedores reais diferentes, vale
  `ia.provider` e um aviso é registrado no log.

Circuit breaker, guardrail e template independem do provedor.

### Variáveis por provedor

Cada provedor tem as próprias variáveis. Nenhuma é compartilhada. Todos os
padrões funcionam sem credenciais.

| Variável | Provedor | Padrão | Descrição |
| --- | --- | --- | --- |
| `LLM_PROVIDER` | — | `simulador` | `simulador`, `openai` ou `llm-externo` |
| `IA_OPENAI_API_KEY` | `openai` | vazio | Chave da API. Nunca versionar. |
| `IA_OPENAI_MODEL` | `openai` | `gpt-4o-mini` | Modelo |
| `IA_OPENAI_BASE_URL` | `openai` | `https://api.openai.com/v1` | Endpoint |
| `LLM_API_KEY` | `llm-externo` | vazio | Chave da API. Nunca versionar. |
| `LLM_MODEL` | `llm-externo` | ver `application.properties` | Modelo |
| `LLM_BASE_URL` | `llm-externo` | ver `application.properties` | Endpoint |
| `LLM_EFFORT` | `llm-externo` | `low` | `low`, `medium`, `high`, `xhigh` ou `max` |
| `LLM_MAX_TOKENS` | `llm-externo` | `2048` | Teto de tokens da resposta |

```bash
# bash
export LLM_PROVIDER=llm-externo
export LLM_API_KEY=<sua-chave>
cd backend && mvn spring-boot:run
```

```powershell
# PowerShell
$env:LLM_PROVIDER = "llm-externo"; $env:LLM_API_KEY = "<sua-chave>"
cd backend; mvn spring-boot:run
```

Na inicialização o log mostra `Provedor de IA selecionado: llm-externo`. Sem
chave, a aplicação sobe normalmente, registra um aviso e todos os lembretes
saem pelo template (`usouFallback=true`).

### Detalhes do provedor `llm-externo`

- Sem retentativas no SDK (`maxRetries=0`). O timeout do SDK é o mesmo
  `ia.lembrete.timeout-ms` (5 s). Repetições ficam a cargo do circuit breaker.
- O fallback do lado do servidor do provedor fica habilitado: se o modelo
  recusar, a própria API tenta um modelo alternativo. Se ainda assim não
  houver texto, entra o template.
- O prompt envia só nome do tutor, nome do pet, nome do veterinário, data e
  horário. Nunca envia e-mail, telefone ou CPF.
- Resposta vazia, recusa ou truncada gera `RespostaIaInvalidaException` e
  conta como resposta inválida. Erro HTTP, JSON malformado ou ausência de
  chave contam como falha.
- Os logs registram apenas modelo, tipo de erro e motivo da rejeição. Não
  registram chave, prompt, texto gerado nem nomes.

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

`CircuitBreakerIa` é um circuit breaker em memória, sem biblioteca adicional.
Cada integração tem a **sua própria instância**: o circuito aberto do lembrete
não afeta o resumo nem a predição.

- **FECHADO**: chamadas liberadas; falhas seguidas são contadas.
- **ABERTO**: depois de `max-falhas` falhas seguidas, nenhuma chamada sai
  durante `reset-ms`. O service vai direto para o fallback (template, resumo
  determinístico ou "score indisponível") sem chamar o serviço externo.
- **TESTE**: passado o intervalo, só uma chamada de prova sai. Sucesso fecha
  o circuito e zera a sequência; falha reabre.

O que conta como falha: erro, timeout, resposta sem texto ou inválida e, no
lembrete, texto rejeitado pelo guardrail.

### Configuração por integração

Os padrões globais valem para toda integração que não definir os seus:

| Propriedade | Padrão | Integrações |
| --- | --- | --- |
| `ia.timeout-ms` | `5000` | padrão de todas |
| `ia.circuit-breaker.max-falhas` | `3` | padrão de todas |
| `ia.circuit-breaker.reset-ms` | `30000` | padrão de todas |
| `ia.lembrete.timeout-ms`, `ia.lembrete.circuit-breaker.*` | padrão global | lembrete |
| `ia.resumo.timeout-ms`, `ia.resumo.circuit-breaker.*` | padrão global | resumo |
| `ia.predicao.timeout-ms` | `NOSHOW_PREDICTOR_TIMEOUT_MS` (`5000`) | predição |
| `ia.predicao.circuit-breaker.*` | padrão global | predição |

Exemplo: `ia.resumo.circuit-breaker.max-falhas=5` muda só o resumo.

O executor do lembrete aceita no máximo quatro tarefas simultâneas, com
threads daemon. Cada chamada usa o timeout configurado e cancela o `Future`
quando ele estoura; o executor é encerrado junto com o serviço. Sob
saturação, novas tarefas são rejeitadas e resolvidas pelo fallback.

Métricas: `CircuitBreakerIa.Metricas` (estado, falhas consecutivas, falhas,
sucessos, chamadas bloqueadas) via `obterMetricas()` em cada service. O
lembrete também conta timeouts, fallbacks e respostas inválidas em
`LembreteGeneratorService.Metricas`. A telemetria é local à instância e não
persiste entre reinicializações.

## Testes

Nenhum teste chama um provedor real.

- `CircuitBreakerIaTest`: transições FECHADO → ABERTO → TESTE → FECHADO/ABERTO
  com relógio controlado.
- `CircuitBreakerIntegracoesTest`: lembrete, resumo e predição vão direto para
  o fallback com o circuito aberto, sem chamar o serviço externo, e os
  circuitos são independentes.
- `IaClientConfigTest` (`ApplicationContextRunner`): padrão, `openai`,
  `llm-externo` sem chave (sobe e usa o template), combinações cruzadas e
  provedor inválido.
- `AnthropicLembreteIaClientTest` usa o SDK verdadeiro contra um servidor HTTP
  local que imita a API: serialização, cabeçalhos e leitura da resposta
  (válida, vazia, recusa, truncada, JSON malformado, HTTP 500/503 e timeout).
- `LembreteDeliveryServiceTest` e `ConsultaControllerHttpTest`: 502 e 504 na
  entrega do e-mail.
