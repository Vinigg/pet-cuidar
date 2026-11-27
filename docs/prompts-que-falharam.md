# Prompts que Falharam e Como Foram Ajustados - Pet Cuidar

Item **1.5** da Entrega Final. Relata casos de saída de IA que falharam, o ajuste
feito no prompt e como a equipe validou o output.

Durante o desenvolvimento das funcionalidades de IA generativa (lembrete e
resumo de histórico), a equipe identificou falhas recorrentes nas primeiras
versões dos prompts. Cada falha foi tratada em **duas camadas**: um ajuste no
prompt (reduz a chance do erro) e uma validação determinística em código
(garante o resultado, independentemente do que o modelo gere). Abaixo, três
casos representativos.

> Observação: os textos dos prompts citados são os reais, versionados no código
> (`AnthropicLembreteIaClient.java`, `OpenAiIaClient.java`). Os exemplos de
> output ruim são ilustrativos — representam o tipo de erro que cada regra
> combate; substituir por um caso real registrado, se houver, reforça o relato.

---

## Caso 1: O modelo trocava ou omitia o nome do pet

**O que falhou:** Na primeira versão do prompt de lembrete, que apenas pedia
"escreva um lembrete de consulta com os dados abaixo", o modelo frequentemente
substituía o nome real do pet por um nome genérico ou carinhoso ("seu pet", "seu
amiguinho") e, em alguns casos, alterava o formato da data e do horário. O
resultado era uma mensagem impessoal ou com informação divergente da consulta.

**Exemplo do problema:** Para a consulta do pet "Thor" às 14:00, o modelo gerou
"Olá! Lembramos que seu pet tem consulta marcada para as duas da tarde", 
perdendo o nome e reescrevendo o horário por extenso.

**Ajuste no prompt:** Adicionada a regra obrigatória: *"Copie exatamente, sem
alterar grafia nem formato, o nome do tutor, o nome do pet, a data (dd/mm/aaaa) e
o horário (hh:mm) informados"*.

**Como a equipe validou o output:** Foi criado um guardrail em código
(`LembreteGuardrail`) que confere, de forma determinística, se o texto gerado
contém exatamente o nome do tutor, o nome do pet, a data e o horário reais. Se
qualquer um estiver ausente ou divergente, o texto da IA é descartado e um
**template fixo** é enviado no lugar. A correção não depende da "boa vontade" do
modelo: o código garante que nenhuma mensagem sai sem os dados corretos.

---

## Caso 2: O modelo inventava informações (telefones, horários, valores)

**O que falhou:** O modelo tendia a "preencher lacunas" do lembrete com dados
plausíveis, porém inexistentes, um telefone da clínica, um segundo horário
("chegue 15 minutos antes"), um valor de consulta ou uma orientação de preparo.
Para um lembrete enviado a um cliente real, qualquer informação inventada é um
risco sério.

**Exemplo do problema:** Numa execução, o modelo acrescentou por conta própria
"Em caso de dúvidas, ligue para (81) 99999-0000", um telefone nunca fornecido
ao sistema.

**Ajuste no prompt:** Adicionada a regra: *"Use somente as informações
fornecidas. Não acrescente outras datas, horários, números, endereços,
telefones, links, e-mails, valores, preparos, orientações clínicas ou nomes"*.

**Como a equipe validou o output:** O guardrail passou a rejeitar o texto se
encontrasse **qualquer dígito além dos dados informados** (data e horário da
consulta). Se aparece um número que não deveria existir, é sinal de telefone,
valor ou horário inventado → texto reprovado, cai para o template fixo. Essa
verificação transforma uma instrução "mole" do prompt numa garantia "dura" no
código.

---

## Caso 3: O resumo do histórico omitia alergias ou sugeria diagnóstico

**O que falhou:** Na funcionalidade de resumo do histórico do pet, a primeira
versão do prompt pedia apenas "resuma o histórico abaixo". O modelo cometia dois
erros opostos: ora resumia demais e **omitia uma alergia crítica** registrada,
ora ia além dos dados e **sugeria um diagnóstico** que não estava nos registros.
Num contexto veterinário, omitir uma alergia pode ter consequências graves.

**Exemplo do problema:** Para um pet com alergia a um anti-inflamatório
registrada no histórico, o resumo gerado mencionava os atendimentos anteriores
mas não citava a alergia; em outra execução, o modelo escreveu "o quadro sugere
uma possível dermatite alérgica", um diagnóstico que ninguém havia feito.

**Ajuste no prompt:** O prompt de resumo passou a instruir *"resuma em 2 ou 3
frases, sem diagnosticar nem inventar fatos"*, e passou a receber a lista de
itens críticos cadastrados como entrada separada.

**Como a equipe validou o output:** A garantia mais importante foi tirada do
texto livre da IA: os **itens críticos (alergias e reações)** passaram a ser
inseridos no resumo por **regra fixa em código**, fora do que o modelo gera.
Assim, mesmo que o modelo omita uma alergia, ela aparece garantidamente. Além
disso, o resumo sempre inclui um link para o **histórico completo**, de modo que
o veterinário nunca depende apenas da versão resumida.

---

## Aprendizado geral

O principal aprendizado da equipe em engenharia de prompt foi que **o prompt
reduz a probabilidade do erro, mas quem garante o resultado é o código**. Para
toda saída de IA voltada ao usuário final, adotamos o mesmo padrão: ajustar o
prompt para orientar o modelo e, em paralelo, construir uma validação
determinística (guardrail, regras fixas e fallback para template) que assegure a
correção mesmo quando o modelo falha. É essa combinação que torna o uso de IA
confiável o suficiente para um sistema que se comunica com clientes reais.