"""
Testes do microservico de predicao de no-show.

Rodar (dentro de microservico-predicao/, com o modelo ja treinado):
    python -m app.gerar_dataset --n 6000
    python -m app.treinar
    python -m pytest
"""
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_ok():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_alto_risco_tem_categoria_alto():
    r = client.post("/predict", json={
        "consultas_anteriores": 6, "faltas_anteriores": 4,
        "dias_antecedencia": 21, "periodo": 2, "idade_pet": 10,
    })
    assert r.status_code == 200
    body = r.json()
    assert 0.0 <= body["score"] <= 1.0
    assert body["categoria"] == "ALTO"
    assert body["motivo"]  # nunca vazio


def test_bom_pagador_tem_score_baixo():
    r = client.post("/predict", json={
        "consultas_anteriores": 8, "faltas_anteriores": 0,
        "dias_antecedencia": 3, "periodo": 1, "idade_pet": 5,
    })
    assert r.status_code == 200
    assert r.json()["categoria"] == "BAIXO"


def test_sem_historico_nao_quebra():
    r = client.post("/predict", json={
        "consultas_anteriores": 0, "faltas_anteriores": 0,
        "dias_antecedencia": 2, "periodo": 0, "idade_pet": 3,
    })
    assert r.status_code == 200
    assert "sem historico" in r.json()["motivo"].lower()


def test_ignora_campo_extra_do_backend():
    """O back-end ainda envia ja_confirmou_antes; o servico deve ignorar sem erro."""
    r = client.post("/predict", json={
        "consultas_anteriores": 6, "faltas_anteriores": 4,
        "dias_antecedencia": 21, "periodo": 2,
        "ja_confirmou_antes": 0, "idade_pet": 10,
    })
    assert r.status_code == 200
    assert r.json()["categoria"] == "ALTO"


def test_resposta_tem_o_contrato_esperado():
    r = client.post("/predict", json={
        "consultas_anteriores": 3, "faltas_anteriores": 1,
        "dias_antecedencia": 10, "periodo": 1, "idade_pet": 4,
    })
    body = r.json()
    assert set(body.keys()) == {"score", "categoria", "motivo"}
    assert body["categoria"] in {"BAIXO", "MEDIO", "ALTO"}
