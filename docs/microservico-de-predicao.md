# Documentação - Microserviço de Predição de No-show

## 1. O que é esta parte do projeto

O **microserviço de predição de no-show** é um serviço independente, escrito em Python, que responde a uma única pergunta: **qual a chance de o tutor faltar a uma consulta?**

Ele recebe os dados de uma consulta, roda um modelo de machine learning e devolve um **score de risco (0 a 1), uma categoria (BAIXO / MÉDIO / ALTO) e um motivo explicável**. É um dos três usos de IA do projeto e o único que é modelo preditivo (os outros dois (gerar lembrete e resumir histórico) são geração de texto, de outra frente).

**Objetivo de negócio:** dar à recepcionista, com antecedência, a informação de quais consultas têm maior risco de falta, para ela priorizar quais confirmar por telefone. Resolve a dor descrita no PRD de "só descobrir quem faltou na hora da consulta vazia".

---

## 2. Como funciona (visão geral)

```
Back-end (Java) ──> POST /predict ──> Microserviço (Python) ──> modelo treinado ──> resposta
                <── score + categoria + motivo ───────────────────────────────
```

O back-end não sabe prever faltas; quando precisa, chama o microserviço pela rede, passando os dados da consulta, e recebe a previsão pronta. Os dois são programas separados: isso permite usar Python (ideal para ML) enquanto o resto do sistema é Java, atualizar o modelo sem mexer no back-end, e manter o agendamento funcionando mesmo se a predição cair.

---

## 3. Componentes que eu construí

| Arquivo | O que faz |
|---|---|
| `app/gerar_dataset.py` | Gera um dataset sintético de treino (a clínica começa sem histórico real de faltas) |
| `app/treinar.py` | Treina o modelo (regressão logística, scikit-learn) e salva em `model/modelo_noshow.joblib` |
| `app/explicacao.py` | Monta o "motivo" legível e define as faixas BAIXO/MÉDIO/ALTO |
| `app/main.py` | API FastAPI: expõe `POST /predict` e `GET /health` |
| `model/modelo_noshow.joblib` | Modelo treinado, carregado pela API |
| `tests/test_predict.py` | 6 testes automáticos cobrindo cenários e contrato |
| `README.md`, `requirements.txt`, `Dockerfile`, `.gitignore` | Documentação, dependências, empacotamento e arquivos ignorados |

---

## 4. O modelo (parte técnica, em linguagem simples)

- **Tipo:** regressão logística — modelo que calcula uma probabilidade de 0 a 1. Foi escolhido (e o DAS exige) por ser simples e **explicável**: dá para saber por que o score deu alto, o que permite justificar para a recepcionista.
- **Como aprende:** o modelo não tem regras escritas à mão. Ele lê os dados de treino e descobre sozinho o peso de cada fator.
- **Fatores (features) usados:** nº de consultas anteriores, nº de faltas anteriores, taxa de faltas, dias de antecedência do agendamento, período do dia e idade do pet.
- **Dados de treino:** como a clínica começa sem histórico real, o modelo é treinado com um **dataset sintético** plausível. Quando houver dados reais, basta substituir o CSV e retreinar (a API não muda).
- **Qualidade atual:** ROC-AUC ≈ 0,78 (escala de 0 a 1; quanto mais perto de 1, melhor).

---

## 5. Contrato da API (para quem integra)

### `POST /predict`
Entrada:
```json
{
  "consultas_anteriores": 6,
  "faltas_anteriores": 4,
  "dias_antecedencia": 21,
  "periodo": 2,
  "idade_pet": 10
}
```
Saída (idêntica ao `PredicaoNoShowDTO` do back-end):
```json
{ "score": 0.77, "categoria": "ALTO", "motivo": "4 falta(s) em 6 consulta(s) anteriores; agendada com 21 dias de antecedencia." }
```
- **score**: 0.0 a 1.0 - probabilidade de falta
- **categoria**: BAIXO (<0.25), MÉDIO (<0.6), ALTO (≥0.6)
- **motivo**: explicação verificável; o score nunca vai sozinho

### `GET /health`
`{ "status": "ok", "modelo_carregado": true }`

**Compatibilidade:** a API ignora campos extras. Se o back-end enviar algo a mais (ex.: `ja_confirmou_antes`, hoje não usado), o serviço ignora sem erro, o back-end não precisa de ajuste.

---

## 6. Como rodar

```bash
cd microservico-predicao
pip install -r requirements.txt
python -m app.gerar_dataset --n 6000     # gera o dataset
python -m app.treinar                    # treina e salva o modelo
uvicorn app.main:app --port 8000         # sobe o serviço (docs em /docs)
python -m pytest                         # roda os 6 testes
```

---

## 7. Regras de negócio atendidas (do DAS)

- O score **nunca** cancela, confirma ou reagenda consulta, é só apoio à decisão humana.
- O score **sempre** vem acompanhado do motivo.
- Modelo **explicável** por design (regressão logística).
- Se o serviço cair, o back-end usa fallback ("score indisponível") e o agendamento continua funcionando.