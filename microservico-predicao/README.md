# Microserviço de Predição de No-show — Pet Cuidar

Serviço Python (FastAPI + scikit-learn) que estima o risco de falta de uma
consulta e devolve **score + categoria + motivo explicável**. É consumido pelo
back-end Java (`NoShowPredictorService.java`) e segue o mesmo formato de
resposta do `PredicaoNoShowDTO` já existente no projeto.

## Estrutura

```
microservico-predicao/
├── app/
│   ├── gerar_dataset.py   # gera dataset sintético (clínica começa sem histórico)
│   ├── treinar.py         # treina a regressão logística e salva o modelo
│   ├── explicacao.py      # monta o "motivo" e as faixas BAIXO/MEDIO/ALTO
│   └── main.py            # API FastAPI (/predict, /health)
├── model/                 # modelo treinado (.joblib) — gerado pelo treino
├── requirements.txt
├── Dockerfile
└── README.md
```

## Como rodar (local)

```bash
pip install -r requirements.txt
python -m app.gerar_dataset --n 6000   # 1) gera data/consultas.csv
python -m app.treinar                  # 2) treina e salva model/modelo_noshow.joblib
uvicorn app.main:app --port 8000       # 3) sobe o serviço
```

Docs interativas: http://localhost:8000/docs

## Contrato da API (para o back-end integrar)

### `POST /predict`

Entrada (o que o back-end Java envia):

```json
{
  "consultas_anteriores": 6,
  "faltas_anteriores": 4,
  "dias_antecedencia": 21,
  "periodo": 2,
  "ja_confirmou_antes": 0,
  "idade_pet": 10
}
```

| campo | tipo | descrição |
|---|---|---|
| consultas_anteriores | int ≥ 0 | total de consultas anteriores do tutor |
| faltas_anteriores | int ≥ 0 | quantas dessas o tutor faltou |
| dias_antecedencia | int ≥ 0 | dias entre o agendamento e a consulta |
| periodo | int 0–2 | 0=manhã, 1=tarde, 2=fim de tarde |
| ja_confirmou_antes | int 0/1 | 1 se já confirmou algum lembrete antes |
| idade_pet | int ≥ 0 | idade do pet em anos |

Saída (idêntica ao `PredicaoNoShowDTO`):

```json
{ "score": 0.86, "categoria": "ALTO", "motivo": "4 falta(s) em 6 consulta(s) anteriores; agendada com 21 dias de antecedencia." }
```

- **score**: 0.0 a 1.0 (probabilidade de falta)
- **categoria**: `BAIXO` (<0.25), `MEDIO` (<0.6), `ALTO` (≥0.6) — mesmas faixas do back-end
- **motivo**: explicação verificável; o score nunca vai sozinho

### `GET /health`
`{ "status": "ok", "modelo_carregado": true }`

## Regras de negócio (DAS)

- O score **nunca** cancela, confirma ou reagenda consulta — é só apoio à decisão humana.
- O score **sempre** vem com `motivo` explicável.
- Se o modelo não estiver disponível, o serviço usa um fallback simples (taxa de faltas) e continua respondendo.

## Observação sobre os dados

A clínica começa **sem histórico real** de faltas, então o modelo é treinado com
um **dataset sintético** (`gerar_dataset.py`). Quando houver dados reais, basta
substituir o CSV e rodar `treinar.py` de novo — a API não muda.
