"""
Gera um dataset sintetico de consultas para treinar o modelo de no-show.

Por que isso existe: a clinica Pet Cuidar comeca SEM historico real de faltas.
Para o MVP, criamos dados plausiveis em que a chance de falta depende de
fatores realistas (faltas anteriores, antecedencia do agendamento, etc.).
O objetivo NAO e acertar a realidade, e ter um modelo explicavel e funcional
end-to-end ate chegarem dados reais.

Uso:
    python -m app.gerar_dataset            # gera data/consultas.csv (5000 linhas)
    python -m app.gerar_dataset --n 20000  # outra quantidade
"""
import argparse
import os
import numpy as np
import pandas as pd

RNG = np.random.default_rng(42)  # semente fixa: dataset reproduzivel


def gerar(n: int) -> pd.DataFrame:
    # --- Features de entrada (as mesmas que o microservico recebera em producao) ---

    # Historico do tutor
    consultas_anteriores = RNG.integers(0, 15, size=n)          # quantas consultas o tutor ja teve
    # faltas nunca podem passar do total de consultas anteriores
    faltas_anteriores = np.array([RNG.integers(0, c + 1) for c in consultas_anteriores])

    # Antecedencia entre o agendamento e a consulta (dias)
    dias_antecedencia = RNG.integers(0, 30, size=n)

    # Faixa de horario da consulta: 0=manha, 1=tarde, 2=fim de tarde
    periodo = RNG.integers(0, 3, size=n)

    # Se ja houve pelo menos um lembrete confirmado antes (0/1)
    ja_confirmou_antes = RNG.integers(0, 2, size=n)

    # Idade do pet (anos) - proxy fraco, mas ajuda o modelo a ser menos trivial
    idade_pet = RNG.integers(0, 18, size=n)

    taxa_faltas = np.divide(
        faltas_anteriores, consultas_anteriores,
        out=np.zeros(n, dtype=float), where=consultas_anteriores > 0,
    )

    # --- Regra latente que define a probabilidade "verdadeira" de faltar ---
    # (o modelo nao ve isto; ele tenta aprender a partir das features acima)
    logit = (
        -1.6                                  # intercepto: a maioria comparece
        + 3.0 * taxa_faltas                   # historico de faltas pesa muito
        + 0.05 * dias_antecedencia            # quanto mais longe, mais esquece
        + 0.25 * (periodo == 2)               # fim de tarde falta um pouco mais
        - 1.1 * ja_confirmou_antes            # quem confirma, comparece mais
        + 0.02 * idade_pet
    )
    prob = 1 / (1 + np.exp(-logit))
    faltou = RNG.binomial(1, prob)            # rotulo: 1 = nao compareceu

    return pd.DataFrame({
        "consultas_anteriores": consultas_anteriores,
        "faltas_anteriores": faltas_anteriores,
        "taxa_faltas": taxa_faltas.round(3),
        "dias_antecedencia": dias_antecedencia,
        "periodo": periodo,
        "ja_confirmou_antes": ja_confirmou_antes,
        "idade_pet": idade_pet,
        "faltou": faltou,
    })


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=5000)
    ap.add_argument("--out", default="data/consultas.csv")
    args = ap.parse_args()

    df = gerar(args.n)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    df.to_csv(args.out, index=False)
    print(f"Dataset gerado: {args.out}  ({len(df)} linhas)")
    print(f"Taxa de faltas no dataset: {df['faltou'].mean():.1%}")
