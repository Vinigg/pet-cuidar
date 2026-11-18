# Roteiro de teste de ponta a ponta (front → back-end → predição → tela)

Os testes automatizados de cada parte usam mocks. Este roteiro valida o fluxo
real com tudo rodando junto. Rode primeiro localmente e depois repita em
produção (trocando as URLs).

## Preparação (local)

```bash
# na raiz do repositorio
docker compose up --build          # back-end (8080) + predicao (rede interna)

# em outro terminal
cd frontend
cp .env.example .env               # REACT_APP_API_URL=http://localhost:8080
npm install
npm start                          # http://localhost:3000
```

Sem Docker: rode `uvicorn app.main:app --port 8000` em `microservico-predicao/`
e `mvn spring-boot:run` em `backend/` (o back-end usa `http://localhost:8000`
por padrão).

## Checklist

| # | Passo | Resultado esperado | OK? |
|---|---|---|---|
| 1 | `docker compose ps` | `predicao` **healthy**, `backend` **running** | |
| 2 | Cadastrar tutor e pet pelo front | Cadastro salvo, sem erro | |
| 3 | Agendar uma consulta para hoje | Consulta aparece no Painel da Recepção | |
| 4 | Olhar a coluna "Risco falta (IA)" | Mostra "Calculando..." e depois Baixo/Médio/Alto com % | |
| 5 | Passar o mouse no selo | Aparece o motivo (ex.: "sem historico...") | |
| 6 | `docker compose logs backend` | Nenhum "Microservico de no-show indisponivel" | |
| 7 | Marcar uma consulta como não compareceu e agendar outra para o mesmo tutor | O score da nova consulta sobe | |
| 8 | `docker compose stop predicao` e clicar em "Atualizar Agenda" | Selo **Indisponível** (cinza), nunca "Baixo"; resto do painel funciona | |
| 9 | Agendar uma consulta com a predição parada | Agendamento funciona normalmente | |
| 10 | `docker compose start predicao`, aguardar 30 s e atualizar | Scores voltam (o circuit breaker fecha após o `reset-ms`) | |
| 11 | Enviar lembrete de uma consulta | Modal mostra o texto (simulador ou provedor real) | |

## Inicialização com a predição indisponível

Além de interromper a predição com o backend já rodando, verifique que ela
não bloqueia a inicialização do backend:

```bash
docker compose stop backend predicao
docker compose up -d backend
docker compose ps --all
```

O backend deve iniciar e a predição deve permanecer parada. Repita os passos
8 e 9 do checklist: o risco fica **Indisponível**, mas o agendamento funciona.
Depois, execute `docker compose up -d predicao`, aguarde a predição ficar
saudável e a janela do circuit breaker (30 segundos por padrão), e atualize
a agenda para conferir a recuperação dos scores.

## Em produção

Repita os passos 2 a 11 pela URL do CloudFront. Pontos extras:

- O navegador não deve mostrar erro de CORS nem de "mixed content" (back-end
  precisa estar em HTTPS).
- A porta 8000 **não** deve responder de fora da instância:
  `curl -m 5 http://<ip-publico>:8000/health` tem que falhar.

Anote data, ambiente e quem executou ao preencher a coluna "OK?".
