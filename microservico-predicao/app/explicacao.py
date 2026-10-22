"""
Gera o "motivo" explicavel que acompanha o score.

Regra de negocio do DAS (criterio de aceite de IA): o score NUNCA aparece
sozinho - sempre vem com uma explicacao simples e verificavel pela
recepcionista (ex.: "2 faltas em 8 consultas anteriores"). Este modulo monta
essa frase a partir dos dados da propria consulta, de forma deterministica
(nao depende do texto livre de nenhuma IA generativa).
"""
from typing import Dict


def categorizar(score: float) -> str:
    """Faixas identicas as ja definidas no back-end (PredicaoNoShowDTO)."""
    if score < 0.25:
        return "BAIXO"
    if score < 0.6:
        return "MEDIO"
    return "ALTO"


def montar_motivo(dados: Dict, score: float) -> str:
    consultas = int(dados.get("consultas_anteriores", 0))
    faltas = int(dados.get("faltas_anteriores", 0))
    antecedencia = int(dados.get("dias_antecedencia", 0))
    confirmou = int(dados.get("ja_confirmou_antes", 0))

    if consultas == 0:
        return "Tutor sem historico de consultas anteriores; risco estimado pela media."

    partes = [f"{faltas} falta(s) em {consultas} consulta(s) anteriores"]
    if antecedencia >= 15:
        partes.append(f"agendada com {antecedencia} dias de antecedencia")
    if confirmou:
        partes.append("ja confirmou presenca em lembretes anteriores")
    return "; ".join(partes).capitalize() + "."
