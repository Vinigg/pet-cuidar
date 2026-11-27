# Integrações de IA e Repositório de Prompts - Pet Cuidar

Documento referente aos itens **1.2** (documentação de integrações de IA e
engenharia de prompt) e **1.4** (repositório de prompts com rastreabilidade) da
Entrega Final.

> Observação: a frente de **predição de no-show** usa um modelo próprio de
> machine learning (regressão logística), **não usa prompt de LLM**, está
> documentada à parte em `docs/microservico-de-predicao.md`. Este documento
> cobre os dois usos de IA baseados em **LLM**: geração de lembrete e resumo de
> histórico.

---

## 1. Serviços de IA utilizados

| Uso no sistema | Tipo de IA | Serviço | Pago/Gratuito |
|---|---|---|---|
| Geração do texto do lembrete | LLM (geração de texto) | OpenAI **ou** Anthropic (configurável) | Pago por uso |
| Resumo do histórico do pet | LLM (geração de texto) | OpenAI **ou** Anthropic (configurável) | Pago por uso |
| Predição de risco de falta (no-show) | Modelo próprio (scikit-learn) | Microserviço interno | Gratuito |

O provedor de LLM é configurável por variável de ambiente, e há um **simulador
local** usado por padrão em desenvolvimento (sem gastar chamadas reais). Em
qualquer cenário de falha, o sistema cai para um **template fixo**, detalhes em
`docs/resiliencia-ia.md`.

---

## 2. Prompts que sustentam o core da aplicação

### 2.1 Lembrete de consulta - prompt de sistema (provedor Anthropic)

```
Você redige lembretes de consulta veterinária enviados pela clínica {clinica}
aos tutores. Escreva em português do Brasil, em tom cordial, claro e objetivo,
uma única mensagem de no máximo três frases e até 400 caracteres, em texto
simples, sem markdown e sem emojis.

Regras obrigatórias:
- Copie exatamente, sem alterar grafia nem formato, o nome do tutor, o nome do
  pet, a data (dd/mm/aaaa) e o horário (hh:mm) informados.
- Use somente as informações fornecidas. Não acrescente outras datas, horários,
  números, endereços, telefones, links, e-mails, valores, preparos, orientações
  clínicas ou nomes.
- Se citar o veterinário, use exatamente o nome informado.
- Responda apenas com o texto do lembrete.
```

**Prompt de usuário** (dados injetados):
```
Dados da consulta:
- Tutor: {nome_tutor}
- Pet: {nome_pet}
- Veterinário: {nome_veterinario}   (quando houver)
- Data: {dd/mm/aaaa}
- Horário: {hh:mm}
```

### 2.2 Lembrete de consulta - prompt (provedor OpenAI)

```
Escreva um lembrete cordial de consulta veterinaria com no maximo 300
caracteres. Inclua literalmente tutor={nome}, pet={nome}, data_hora={data} as
{hora} [e veterinario={nome}]. Nao invente informacoes.
```
(mensagem de sistema: "Responda apenas com o texto solicitado, em português brasileiro.", temperature 0.2)

### 2.3 Resumo do histórico do pet - prompt (OpenAI)

```
Resuma em 2 ou 3 frases o historico veterinario fornecido, sem diagnosticar nem
inventar fatos. Pet={nome}. Atendimentos={lista}. Itens criticos
cadastrados={itens}.
```

---

## 3. Camada de validação (guardrail determinístico)

Toda saída de LLM voltada ao usuário passa por uma validação **em código** antes
de ser usada, a IA nunca fala direto com o usuário final. O guardrail do
lembrete verifica, por regra fixa, que o texto gerado:

- contém **exatamente** o nome do tutor, o nome do pet, a data (dd/MM/yyyy) e o
  horário (hh:mm) informados;
- **não contém nenhum outro número** além dos dados fornecidos (qualquer dígito
  a mais indica data, horário, telefone ou valor inventado → texto reprovado).

Se o texto é reprovado, o serviço descarta a saída da IA e envia um **template
fixo** no lugar. O lembrete nunca é bloqueado. O mesmo princípio de "separar a
saída da IA do dado validado" está no banco (`texto_gerado_ia` × `texto_validado`).

Para o resumo do histórico, os **itens críticos (alergias/reações)** são
garantidos por regra fixa, fora do texto livre da IA.

---

## 4. Configuração (variáveis de ambiente)

| Variável | Para quê |
|---|---|
| `IA_PROVIDER` / `LLM_PROVIDER` | escolhe `openai`, `anthropic` ou simulador |
| `LLM_API_KEY`, `LLM_MODEL`, `LLM_BASE_URL` | credenciais e modelo do LLM |
| `NOSHOW_PREDICTOR_BASE_URL` | URL do microserviço de predição |
| `EMAIL_PROVIDER`, `RESEND_API_KEY`, `EMAIL_FROM` | envio de e-mail |

Chaves **não** ficam no código nem no Git, ver `DEPLOY.md` e `.env.example`.

---

## 5. Repositório de prompts - histórico de alterações (item 1.4)

A rastreabilidade das alterações dos prompts é dada pelo **histórico de commits
do GitHub** dos arquivos `AnthropicLembreteIaClient.java` e `OpenAiIaClient.java`
(cada mudança aparece no `git log`). A tabela abaixo consolida a evolução dos
prompts ao longo do desenvolvimento, do rascunho inicial à versão validada em uso.

| Versão | Prompt | O que mudou | Motivo |
|---|---|---|---|
| v1 → v2 | Lembrete (sistema) | Copiar **exatamente** nome do tutor, do pet, data e horário | Modelo trocava o nome do pet por um genérico ou alterava o formato da data/hora |
| v2 → v3 | Lembrete (sistema) | "Use somente as informações fornecidas; não acrescente outros números, telefones, valores ou orientações" | Modelo inventava dados inexistentes (telefone, segundo horário, orientações) |
| v3 → v4 | Lembrete (sistema) | Restrições de formato: máx. 3 frases, até 400 caracteres, texto simples, sem markdown/emojis; "responda apenas com o texto" | Modelo devolvia texto longo, formatado ou com comentários antes/depois |
| v1 → v2 | Resumo do histórico | "Sem diagnosticar nem inventar fatos" | Modelo sugeria diagnósticos que não estavam nos dados |
| v2 → v3 | Resumo do histórico | Recebe a lista de itens críticos como entrada; inclusão garantida por regra fixa no código | Modelo resumia demais e omitia alergias graves |

As mudanças nos prompts evoluíram junto com o **guardrail de validação em
código** (`LembreteGuardrail`, ver seção 3): cada falha observada gerou ao mesmo
tempo um ajuste no prompt e uma garantia determinística no código.