"""
Microservico de Predicao de No-show (Pet Cuidar).

Servico Python separado do back-end Java. Recebe os dados de uma consulta,
roda o modelo de regressao logistica treinado e devolve:
    { "score": 0.0-1.0, "categoria": "BAIXO|MEDIO|ALTO", "motivo": "..." }

Esse formato de RESPOSTA e identico ao PredicaoNoShowDTO ja existente no
back-end, para o NoShowPredictorService.java consumir sem mudar nada.

Rodar:
    uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
Docs interativas: http://localhost:8000/docs
"""
import os
import joblib
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.explicacao import categorizar, montar_motivo

MODEL_PATH = os.getenv("MODEL_PATH", "model/modelo_noshow.joblib")

app = FastAPI(title="Pet Cuidar - Microservico de Predicao de No-show", version="1.0.0")

def _carregar_modelo():
    if os.path.exists(MODEL_PATH):
        return joblib.load(MODEL_PATH)
    return None


_model = _carregar_modelo()  # carregado ja na importacao


@app.on_event("startup")
def recarregar_modelo():
    global _model
    if _model is None:
        _model = _carregar_modelo()


# ---- Contrato de ENTRADA (o que o back-end Java envia) ----
class PredicaoRequest(BaseModel):
    consultas_anteriores: int = Field(0, ge=0, description="Total de consultas anteriores do tutor")
    faltas_anteriores: int = Field(0, ge=0, description="Quantas dessas o tutor faltou")
    dias_antecedencia: int = Field(0, ge=0, description="Dias entre agendamento e consulta")
    periodo: int = Field(0, ge=0, le=2, description="0=manha, 1=tarde, 2=fim de tarde")
    ja_confirmou_antes: int = Field(0, ge=0, le=1, description="1 se ja confirmou algum lembrete antes")
    idade_pet: int = Field(0, ge=0, description="Idade do pet em anos")


# ---- Contrato de SAIDA (identico ao PredicaoNoShowDTO do back-end) ----
class PredicaoResponse(BaseModel):
    score: float
    categoria: str
    motivo: str


@app.get("/health")
def health():
    return {"status": "ok", "modelo_carregado": _model is not None}


@app.post("/predict", response_model=PredicaoResponse)
def predict(req: PredicaoRequest):
    dados = req.model_dump()
    # taxa_faltas e derivada aqui (feature usada no treino)
    dados["taxa_faltas"] = (
        dados["faltas_anteriores"] / dados["consultas_anteriores"]
        if dados["consultas_anteriores"] > 0 else 0.0
    )

    if _model is None:
        # fallback deterministico se o modelo nao estiver disponivel
        score = round(dados["taxa_faltas"], 2)
    else:
        features = _model["features"]
        X = pd.DataFrame([{f: dados[f] for f in features}])
        score = round(float(_model["pipeline"].predict_proba(X)[:, 1][0]), 2)

    return PredicaoResponse(
        score=score,
        categoria=categorizar(score),
        motivo=montar_motivo(dados, score),
    )
