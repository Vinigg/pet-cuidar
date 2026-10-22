"""
Treina o modelo de predicao de no-show (regressao logistica).

Escolha de modelo: regressao logistica, como define o DAS (Secao 4.1) - simples,
rapido e EXPLICAVEL. Os coeficientes dizem o peso de cada fator, o que permite
gerar o "motivo" que a recepcionista precisa ver.

Uso:
    python -m app.treinar
Gera:
    model/modelo_noshow.joblib   (pipeline: scaler + logistic regression + metadados)
"""
import os
import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.model_selection import train_test_split
from sklearn.metrics import roc_auc_score, classification_report, confusion_matrix

FEATURES = [
    "consultas_anteriores",
    "faltas_anteriores",
    "taxa_faltas",
    "dias_antecedencia",
    "periodo",
    "ja_confirmou_antes",
    "idade_pet",
]
TARGET = "faltou"


def treinar(csv_path="data/consultas.csv", out_path="model/modelo_noshow.joblib"):
    df = pd.read_csv(csv_path)
    X = df[FEATURES]
    y = df[TARGET]

    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    pipe = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(max_iter=1000, class_weight="balanced")),
    ])
    pipe.fit(X_tr, y_tr)

    # --- Avaliacao ---
    proba = pipe.predict_proba(X_te)[:, 1]
    pred = (proba >= 0.5).astype(int)
    auc = roc_auc_score(y_te, proba)
    print(f"ROC-AUC (teste): {auc:.3f}")
    print("\nMatriz de confusao:\n", confusion_matrix(y_te, pred))
    print("\nRelatorio:\n", classification_report(y_te, pred, digits=3))

    # Coeficientes (para o "motivo" explicavel e para sanidade)
    coefs = dict(zip(FEATURES, pipe.named_steps["clf"].coef_[0]))
    print("Coeficientes (impacto de cada feature):")
    for f, c in sorted(coefs.items(), key=lambda kv: -abs(kv[1])):
        print(f"  {f:22s} {c:+.3f}")

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    joblib.dump({"pipeline": pipe, "features": FEATURES, "auc": float(auc)}, out_path)
    print(f"\nModelo salvo em: {out_path}")


if __name__ == "__main__":
    treinar()
