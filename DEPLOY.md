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
de falha, timeout ou resposta inválida, usa o fallback "score indisponível", ou seja, o agendamento continua funcionando mesmo se este serviço estiver fora.

O modelo treinado (`model/modelo_noshow.joblib`) já vai versionado no
repositório, então **não é necessário treinar no deploy**.

### Opção A - Container (recomendada)

O serviço já tem um `Dockerfile` pronto.

1. Build e publicação da imagem (ex.: no **Amazon ECR**):
   ```bash
   cd microservico-predicao
   docker build -t pet-cuidar-predicao .
   # autenticar no ECR, criar o repositorio e dar push da imagem
   ```
2. Subir a imagem num serviço de container gerenciado (**AWS App Runner** ou
   **ECS/Fargate**), expondo a porta **8000**.
3. Anotar a URL pública gerada (ex.: `https://<id>.awsapprunner.com`).

### Opção B - EC2 simples (mesma instância do back-end ou outra)

1. Instalar Python 3.11+ na instância.
2. Copiar a pasta `microservico-predicao/` e instalar as dependências:
   ```bash
   cd microservico-predicao
   pip install -r requirements.txt
   ```
3. Rodar o serviço (de preferência atrás de Nginx, como `systemd` service):
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

### Ligando o back-end ao microserviço

Depois de publicar, aponte o back-end para a URL do serviço, definindo a
variável de ambiente no Elastic Beanstalk/EC2 do back-end:

- `NOSHOW_PREDICTOR_BASE_URL=https://<url-do-microservico>`

Se a variável não for definida, o back-end usa o default `http://localhost:8000`
(útil só em desenvolvimento, com os dois serviços na mesma máquina).

### Verificação rápida

- `GET https://<url-do-microservico>/health` deve responder
  `{"status":"ok","modelo_carregado":true}`.
- Se `modelo_carregado` vier `false`, o arquivo `model/modelo_noshow.joblib`
  não subiu junto — inclua-o na imagem/instância.

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

- **OpenAI:** `IA_PROVIDER=openai`, com `LLM_API_KEY`, `LLM_MODEL` e
   `LLM_BASE_URL`.
- **Anthropic:** `LLM_PROVIDER=anthropic`, com `LLM_API_KEY` e, se necessario,
   `LLM_MODEL` e `LLM_BASE_URL`. A ativacao deve aguardar o ajuste da selecao de
   clientes IA: na configuracao atual, o simulador e o cliente Anthropic podem
   ser registrados simultaneamente.
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
