# Relatório — Microserviço de Predição de No-show

**Projeto:** Sistema de Agendamento Pet Cuidar (Desafio DAS — Turma A / Squad 49, GrowUp)
**Responsável:** Larissa
**Frente:** Microserviço de Predição de No-show (Python)
**Status:** Serviço funcional e testado localmente; falta integração com o back-end e deploy.

---

## 1. O que é a essa task

Construir um **microserviço de predição de no-show**: um serviço Python separado, que recebe os dados de uma consulta e responde qual a **chance de o tutor faltar**, devolvendo um **score + categoria + motivo explicável**.

É um dos três usos de IA do projeto e o único que é **modelo preditivo** (os outros dois — gerar lembrete e resumir histórico — são geração de texto, de outra frente). Corresponde ao "Microserviço de Predição" desenhado no Nível 2 (Containers) e no Deployment do documento de arquitetura.

**Objetivo de negócio:** resolver a dor da recepcionista de "só descobrir quem faltou na hora da consulta vazia". Com o score antecipado, ela prioriza quais consultas confirmar por telefone.

---

## 2. O que a essas parte faz

O serviço recebe os dados de uma consulta e devolve:

```json
{ "score": 0.86, "categoria": "ALTO", "motivo": "4 falta(s) em 6 consulta(s) anteriores; agendada com 21 dias de antecedencia." }
```

- **score**: probabilidade de falta, de 0.0 a 1.0.
- **categoria**: `BAIXO` (<0.25), `MEDIO` (<0.6), `ALTO` (≥0.6) —> mesmas faixas já definidas no back-end.
- **motivo**: explicação curta e verificável, montada por regra fixa (não por texto livre de IA).

**Regras de negócio atendidas (do DAS):**
- O score **nunca** cancela, confirma ou reagenda consulta, é só apoio à decisão humana.
- O score **sempre** vem acompanhado do motivo.
- Se o serviço cair, o back-end usa um fallback ("score indisponível") e o agendamento continua funcionando.
- Modelo **explicável** por design (regressão logística), para a decisão poder ser justificada à recepcionista.

---

## 3. O que já está pronto (feito e testado)

| Item | Arquivo | O que faz |
|---|---|---|
| Gerador de dataset | `app/gerar_dataset.py` | Cria dados sintéticos de treino (a clínica começa sem histórico real) |
| Treino do modelo | `app/treinar.py` | Treina a regressão logística (scikit-learn) e salva o modelo |
| Explicação/faixas | `app/explicacao.py` | Monta o motivo legível e as faixas BAIXO/MÉDIO/ALTO |
| API | `app/main.py` | Expõe `POST /predict` e `GET /health` (FastAPI) |
| Modelo treinado | `model/modelo_noshow.joblib` | "Cérebro" congelado, carregado pela API |
| Infra/doc | `requirements.txt`, `Dockerfile`, `.gitignore`, `README.md` | Dependências, empacotamento e documentação do contrato |

**Validação feita localmente:**
- Modelo treinou com **ROC-AUC ≈ 0.81** (boa separação para dados sintéticos).
- `/health` retornou `modelo_carregado: true`.
- `/predict` testado com casos reais:
  - alto risco (muitas faltas, agendado com antecedência) → `0.86 ALTO`;
  - bom pagador (sem faltas, já confirmou antes) → `0.07 BAIXO`;
  - tutor sem histórico → `0.0 BAIXO`.

---

## 4. Como o modelo decide (resumo técnico)

Uso **regressão logística**: cada fator da consulta recebe um peso e a soma é convertida em probabilidade (0 a 1). Os pesos **não foram escritos à mão**, o modelo os aprendeu a partir dos dados de treino. Os fatores de maior peso aprendidos foram: taxa de faltas anteriores (aumenta muito o risco), já ter confirmado antes (diminui) e antecedência do agendamento (aumenta).

Como a clínica ainda **não tem histórico real**, o treino usa um **dataset sintético** plausível. Quando houver dados reais, basta substituir o CSV e rodar o treino de novo (**a API não muda**).

---

## 5. De quem a essa parte depende

| Dependência | De quem | Situação |
|---|---|---|
| Contrato de dados (quais campos o banco consegue fornecer) | **Diogo** (Dados & Banco) | Pendente —> preciso confirmar se os campos de entrada do `/predict` batem com o que existe/é calculável no banco |
| Dados reais para treino | histórico da clínica | Não existe ainda, por isso uso dataset sintético (decisão consciente para o MVP) |

**Campos de entrada que o serviço espera hoje:** `consultas_anteriores`, `faltas_anteriores`, `dias_antecedencia`, `periodo`, `ja_confirmou_antes`, `idade_pet`.

---

## 6. Quem vai precisar dessa parte e como

| Quem consome | Como |
|---|---|
| **Luan** (Integrações IA, back-end) | O `NoShowPredictorService.java` hoje é um *placeholder* (calcula o score com uma regrinha em Java). Ele vai trocar esse cálculo por uma chamada HTTP ao meu `POST /predict`, guardar o resultado na entidade `PredicaoNoShow` e expor no endpoint `GET /consultas/{id}/predicao-no-show`. O formato de resposta já é idêntico ao `PredicaoNoShowDTO`, então encaixa sem adaptação. |
| **Yasmin** (Front) | Exibe o score em 3 faixas no painel da recepcionista, **com o motivo ao lado** (indireto — consome via back-end, não direto do meu serviço). |
| **Wesley** (DevOps/AWS) | Vai subir o serviço em produção; por isso já deixei o `Dockerfile` pronto. |

---

## 7. O que falta para concluir 100% essa task

1. **Fechar o contrato de entrada com o Diogo** —> confirmar que os campos que o `/predict` espera são obtíveis a partir do banco.
2. **Combinar a URL do serviço com o Luan** —> para ele apontar a chamada Java para o meu `/predict` (ex.: configurável por `ia.predicao.url`).
3. *(Fase de deploy, com o Wesley)* **Publicar na AWS** usando o `Dockerfile`.
4. *(Futuro, quando houver dados reais)* **Retreinar** o modelo com dados reais, sem alterar a API.

**Resumo:** o núcleo técnico dessa task está concluído e validado. O que resta é **integração** (Diogo + Luan), **versionamento** (PR) e **deploy** (Wesley).