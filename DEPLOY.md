# Notas de Deploy — AWS

Este documento descreve uma forma simples de publicar o MVP na AWS, coerente
com as premissas do PRD (uma unica clinica, baixo volume de usuarios) e com o
que foi descrito na Secao 4 do documento de arquitetura.

## Visao geral

```
[ Usuario ]
     |
     v
[ CloudFront + S3 ]  <-- front-end React (build estatico)
     |
     v  (chamadas REST via HTTPS)
[ Elastic Beanstalk / EC2 ]  <-- back-end Spring Boot (JAR)
     |                               |
     |                               v  (HTTP, risco de no-show)
     |                       [ Microservico FastAPI ]  <-- predicao (Python)
     v
[ arquivo petcuidar.db no volume da instancia ]
```

## Front-end (React) — S3 + CloudFront

1. Gerar o build de producao:
   ```bash
   cd frontend
   npm install
   npm run build
   ```
2. Criar um bucket S3 (ex.: `pet-cuidar-frontend`) com hospedagem de site
   estatico habilitada, e enviar o conteudo de `build/`.
3. Criar uma distribuicao CloudFront apontando para o bucket, para HTTPS e
   cache do conteudo estatico.
4. Definir `REACT_APP_API_URL` (no `.env` usado durante o `npm run build`)
   apontando para a URL publica do back-end (passo seguinte).

## Back-end (Spring Boot) — Elastic Beanstalk

Opcao mais simples para o time nesta fase do projeto (evita gerenciar
infraestrutura manualmente):

1. Gerar o JAR executavel:
   ```bash
   cd backend
   mvn clean package
   ```
2. Criar uma aplicacao no **Elastic Beanstalk** com a plataforma "Java (Corretto
   17)" e fazer o upload do JAR gerado em `target/`.
3. Configurar variaveis de ambiente no Elastic Beanstalk (Configuration >
   Software > Environment properties):
   - `SERVER_PORT=5000` (porta padrao esperada pelo Elastic Beanstalk para Java)
   - `APP_CORS_ALLOWED_ORIGIN=https://<dominio-do-cloudfront>`
4. Habilitar HTTPS no load balancer do ambiente (certificado via **AWS
   Certificate Manager**).

### Alternativa mais simples (EC2 unica)

Para o MVP, tambem e possivel subir uma instancia EC2 (ex.: t3.micro, dentro
do free tier), instalar o Java 17 e rodar o JAR diretamente com
`java -jar pet-cuidar-backend.jar`, atras de um Nginx como proxy reverso com
HTTPS (Let's Encrypt) ou de um Application Load Balancer com certificado do
ACM.

## Microserviço de predição de no-show (Python / FastAPI)

O cálculo do risco de falta roda num serviço Python separado, em
`microservico-predicao/`. O back-end Spring Boot o consome por HTTP e, em caso
de falha, timeout ou resposta inválida, usa o fallback "score indisponível"
(`categoria: INDISPONIVEL`), ou seja, o agendamento continua funcionando mesmo
se este serviço estiver fora.

O modelo treinado (`model/modelo_noshow.joblib`) já vai versionado no
repositório, então **não é necessário treinar no deploy**. Se o arquivo não
estiver presente, o serviço **não sobe** e registra o motivo no log.

O microserviço **não deve ficar público**: ele não tem autenticação e só o
back-end precisa chamá-lo. Mantenha a porta 8000 fechada para a internet.

### Deploy recomendado — EC2 única com Docker Compose (back-end + predição)

Como o banco é SQLite em arquivo, o back-end já precisa rodar em **uma única
instância**. Colocar o microserviço na mesma instância é o caminho mais simples
e mais seguro: a comunicação fica na rede interna do Docker e nada além do
back-end é exposto. O `docker-compose.yml` na raiz do repositório já faz isso.

1. Criar uma EC2 (ex.: t3.small; a t3.micro fica justa para JVM + Python) com
   Amazon Linux 2023 e instalar Docker e o plugin Compose.
2. Security group: liberar **22** (só para o IP do time) e **80/443** (ou 8080,
   se ficar sem proxy). **Não** liberar a 8000.
3. Clonar o repositório na instância e criar um `.env` na raiz (não commitar):
   ```bash
   APP_CORS_ALLOWED_ORIGIN=https://<dominio-do-cloudfront>
   # opcionais (simuladores por padrao):
   # LLM_PROVIDER=openai
   # IA_OPENAI_API_KEY=...
   # IA_OPENAI_MODEL=gpt-4o-mini
   # IA_OPENAI_BASE_URL=https://api.openai.com/v1
   # ou, para usar o provedor llm-externo:
   # LLM_PROVIDER=llm-externo
   # LLM_API_KEY=...
   # EMAIL_PROVIDER=resend
   # RESEND_API_KEY=...
   # EMAIL_FROM=...
   # LEMBRETE_JOB_ENABLED=true
   ```
4. Subir:
   ```bash
   docker compose up -d --build
   docker compose ps          # predicao deve aparecer como "healthy"
   ```
5. HTTPS: colocar um Nginx com Let's Encrypt (ou um ALB com certificado do ACM)
   na frente da porta 8080 do back-end. O front no CloudFront é HTTPS, então o
   navegador bloqueia chamadas para um back-end só em HTTP.
6. No build do front, `REACT_APP_API_URL=https://<dominio-do-back-end>`.

O back-end encontra o microserviço por `NOSHOW_PREDICTOR_BASE_URL=http://predicao:8000`,
que o compose já define. O banco fica no volume `petcuidar-data` (`/data/petcuidar.db`
dentro do container): é esse arquivo que entra na rotina de backup.

O backend inicia independentemente da saúde da predição. Se o modelo não
carregar ou o serviço estiver fora, as consultas continuam disponíveis e o
risco aparece como **Indisponível**. O health check da predição continua ativo
para diagnóstico. Para subir somente o backend, use `docker compose up -d backend`.
Após recuperar a predição, aguarde a janela do circuit breaker antes de
verificar os scores novamente (30 segundos por padrão).

### Alternativas

- **Container gerenciado (App Runner ou ECS/Fargate):** o `Dockerfile` do
  microserviço funciona como está (porta 8000, health check em `/health`).
  Nesse caso o serviço precisa ficar em rede privada (VPC, sem URL pública),
  acessível só pelo back-end, e `NOSHOW_PREDICTOR_BASE_URL` aponta para o
  endereço interno.
- **Sem Docker, na mesma EC2 do back-end:** instalar Python 3.12, rodar
  `pip install -r requirements.txt` em `microservico-predicao/` e subir
  `uvicorn app.main:app --host 127.0.0.1 --port 8000` como serviço `systemd`.
  Escutando em `127.0.0.1`, a porta não fica exposta; o back-end usa o default
  `http://localhost:8000` e não precisa de configuração.

### Verificação rápida

- Na instância: `docker compose ps` deve mostrar o `predicao` como **healthy**
  (o health check do container chama `/health`). Na alternativa sem Docker,
  `curl -s http://localhost:8000/health` deve responder
  `{"status":"ok","modelo_carregado":true}`.
- Se o container `predicao` reiniciar em loop, veja `docker compose logs predicao`:
  a mensagem "Modelo de no-show nao encontrado" indica que o `.joblib` não foi
  copiado para a imagem.
- Pelo navegador: no painel da recepção, consultas agendadas devem mostrar
  "Baixo/Médio/Alto (xx%)". "Indisponível" em todas indica que o back-end não
  está alcançando o microserviço.

## Banco de dados (SQLite) — limitacao importante

O grupo optou por **SQLite** como banco principal. Isso funciona bem para o
MVP rodando em **uma unica instancia**, pois o arquivo `petcuidar.db` fica no
disco dessa instancia. Pontos de atencao para producao na AWS:

- **Nao escalar horizontalmente** o back-end (varias instancias) enquanto o
  banco for SQLite em arquivo local — cada instancia teria seu proprio
  arquivo, com dados divergentes.
- Fazer **backup periodico** do arquivo `petcuidar.db` (ex.: job agendado
  copiando o arquivo para um bucket S3), ja que ele vive no disco de uma
  unica instancia (EBS).
- Caminho de evolucao natural, caso a clinica cresça ou o time queira alta
  disponibilidade: migrar para **Amazon RDS (PostgreSQL)**, trocando apenas
  as propriedades `spring.datasource.*` e a dependencia do dialeto em
  `application.properties`/`pom.xml` — o restante do codigo (entidades,
  repositories, services) nao precisa mudar, pois usa Spring Data JPA.

## Segredos e integracoes externas

As integracoes externas de LLM e e-mail usam simuladores locais por padrao.
Chaves e credenciais **nao devem** ser gravadas no `application.properties`
nem commitadas no Git. Armazene-as no **AWS Systems Manager Parameter Store**
(ou **Secrets Manager**) e injete-as como variaveis de ambiente no Elastic
Beanstalk/EC2.

- **Provedor de IA:** `LLM_PROVIDER` = `simulador` (padrao), `openai` ou
   `llm-externo`. Exatamente um cliente e registrado; valor invalido impede a
   inicializacao. Cada provedor tem as proprias variaveis, sem compartilhamento:
   - `openai`: `IA_OPENAI_API_KEY` e, se necessario, `IA_OPENAI_MODEL` e
     `IA_OPENAI_BASE_URL`.
   - `llm-externo`: `LLM_API_KEY` e, se necessario, `LLM_MODEL`,
     `LLM_BASE_URL`, `LLM_EFFORT` e `LLM_MAX_TOKENS`.
   - Exemplo: `LLM_PROVIDER=llm-externo` e `LLM_API_KEY=<sua-chave>`.
   - **Migracao:** quem usava `IA_PROVIDER=openai` com `LLM_API_KEY`,
     `LLM_MODEL` e `LLM_BASE_URL` deve passar a chave, o modelo e o endpoint
     para `IA_OPENAI_API_KEY`, `IA_OPENAI_MODEL` e `IA_OPENAI_BASE_URL`.
     `IA_PROVIDER` continua aceito quando `LLM_PROVIDER` nao esta definido.
- **Resend:** `EMAIL_PROVIDER=resend`, `RESEND_API_KEY` e `EMAIL_FROM`.
- **Preditor de no-show:** `NOSHOW_PREDICTOR_BASE_URL`, apontando para o
   servico FastAPI publicado.
- **Job de lembretes:** `LEMBRETE_JOB_ENABLED=true`, somente depois de validar
   o provedor de e-mail.
- **Fuso horario:** configure `LEMBRETE_TIME_ZONE` se o deploy nao operar em
   `America/Recife`.

Os provedores externos continuam nao homologados enquanto nao houver evidencia
de chamadas reais controladas e dos respectivos limites e contas. Sem uma chave
de LLM, a aplicacao pode usar o template de fallback para gerar os lembretes.

## Resumo dos custos (nivel MVP)

Todos os componentes sugeridos acima (EC2/Elastic Beanstalk t3.micro, S3,
CloudFront, volume EBS pequeno) cabem, no minimo nos primeiros meses, dentro
do **AWS Free Tier**, adequado ao volume de uso descrito no PRD (uma clinica,
poucos veterinarios, uso concentrado em horario comercial).
