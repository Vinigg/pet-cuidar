# Integrações atribuídas a Luan

Esta entrega completa a frente **Back-end Integrações IA** descrita em
`Divisão de tarefas - Grow up.pdf`. O código mantém adapters locais como padrão,
para que testes e desenvolvimento nunca dependam de credenciais ou rede.

## Escopo entregue

### Lembretes por LLM

- Adapter HTTP compatível com OpenAI, configurável por ambiente.
- Timeout de 5 segundos, circuit breaker e métricas.
- Guardrail determinístico para tutor, pet e data/hora.
- Template fixo quando o provedor falha, expira ou devolve conteúdo inválido.
- Texto bruto e texto validado persistidos separadamente.

### E-mail e job de 24 horas

- Porta `EmailSender`, adapter local e adapter Resend.
- Uso exclusivo do e-mail cadastrado do tutor e do texto validado.
- Até três tentativas com backoff exponencial para falhas transitórias.
- Status, tentativas, último erro e data da tentativa persistidos.
- Chave de idempotência por consulta e relação 1:1 no banco.
- Job configurável que busca consultas agendadas/confirmadas 24 horas antes,
  no fuso definido, sem reenviar canceladas ou já processadas.

### Predição de no-show

- Cliente HTTP para `POST /predict` do microserviço FastAPI.
- Contrato com as seis features documentadas no microserviço.
- URL e timeout configuráveis.
- Validação de score, faixa e motivo.
- Falha ou resposta malformada retorna `score: null`, categoria
  `INDISPONIVEL` e motivo explícito; esse estado não é persistido como risco.
- Predição válida é persistida sem duplicar a relação 1:1 e devolve
  `dataCalculo`.

### Resumo de histórico

- Resumo por LLM com alvo de 2–3 frases e fallback para o histórico bruto.
- Timeout configurável.
- Itens críticos ficam em campo próprio e também são acrescentados por regra
  determinística, mesmo se o LLM omitir ou contradizer a informação.
- Resposta informa se houve fallback e sempre inclui o link do histórico.

## Configuração

| Variável | Padrão | Finalidade |
| --- | --- | --- |
| `IA_PROVIDER` | `simulator` | Use `openai` somente para chamada externa explícita |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | API compatível com OpenAI |
| `LLM_API_KEY` | vazio | Credencial protegida do LLM |
| `LLM_MODEL` | `gpt-4o-mini` | Modelo de texto |
| `NOSHOW_PREDICTOR_BASE_URL` | `http://localhost:8000` | Microserviço FastAPI |
| `NOSHOW_PREDICTOR_TIMEOUT_MS` | `5000` | Timeout da predição |
| `EMAIL_PROVIDER` | `local` | Use `resend` para envio externo |
| `RESEND_API_KEY` | vazio | Credencial protegida do Resend |
| `EMAIL_FROM` | `no-reply@petcuidar.local` | Remetente validado no provedor |
| `EMAIL_MAX_ATTEMPTS` | `3` | Máximo de tentativas de envio |
| `LEMBRETE_JOB_ENABLED` | `false` | Habilite somente com provedor de e-mail real configurado |
| `LEMBRETE_ANTECEDENCIA_HORAS` | `24` | Antecedência máxima e janela de recuperação do job |
| `LEMBRETE_TIME_ZONE` | `America/Recife` | Fuso usado pelo agendador |

## Executar e testar

```bash
# microserviço de predição
cd microservico-predicao
pip install -r requirements.txt
uvicorn app.main:app --port 8000

# backend
cd ../backend
mvn test
mvn spring-boot:run
```

O contrato dos endpoints da frente está em `docs/openapi-integracoes.yaml`.

## Limites honestos

- Os adapters externos possuem testes com servidores simulados, mas contas
  reais de LLM e Resend não foram homologadas nesta entrega.
- A aplicação ainda não possui JWT; controle por recepcionista/veterinário é
  dependência de outra frente.
- O histórico enviado ao preditor é agregado por tutor, incluindo consultas
  anteriores de seus diferentes pets.
- O campo de confirmação anterior é enviado como `0` porque essa informação
  ainda não é persistida pelo domínio atual.
