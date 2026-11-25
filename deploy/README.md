# Deploy na AWS — EC2 única com Docker Compose

Tudo roda numa só instância: Nginx (front React + proxy da API), back-end
Spring Boot e o microserviço Python de no-show. O backend usa SQLite no volume local ou PostgreSQL no RDS.

```
Navegador ──► EC2 :80  Nginx ──┬─ /        → build do React
                               └─ /api/*   → backend:8080 ──► predictor:8000
                                                  │
                                          SQLite no volume petcuidar-data ou RDS PostgreSQL via TLS
```

Como front e API ficam na mesma origem, não há problema de CORS nem de
"mixed content" (front em HTTPS chamando API em HTTP).

## Passo a passo (console da AWS)

1. **Região:** `sa-east-1` (São Paulo) fica mais perto; `us-east-1` é mais barata.
2. **EC2 → Launch instance**
   - AMI: **Amazon Linux 2023**
   - Tipo: `t3.small` (2 GB). `t3.micro` (1 GB) funciona com o swap que o script cria, mas o primeiro build fica lento.
   - Key pair: crie uma e guarde o `.pem`.
   - Security group: liberar **HTTP 80** (0.0.0.0/0) e **SSH 22** só para "My IP".
   - Disco: 20 GB gp3.
   - *Advanced details → User data*: cole o conteúdo de `deploy/ec2-setup.sh`.
3. **Elastic IP:** aloque um e associe à instância (senão o IP muda a cada stop/start).
   Depois disso, ajuste `APP_CORS_ALLOWED_ORIGIN` em `/opt/pet-cuidar/deploy/.env.prod` e rode o comando de atualização abaixo.
4. Aguarde ~5–10 min (build do Maven/npm) e abra `http://<ELASTIC_IP>`.

Acompanhar o primeiro boot via SSH:

```bash
ssh -i chave.pem ec2-user@<ELASTIC_IP>
sudo tail -f /var/log/cloud-init-output.log
cd /opt/pet-cuidar && sudo docker compose -f docker-compose.prod.yml logs -f backend
```

## Atualizar depois de um merge na main

```bash
cd /opt/pet-cuidar && sudo git pull && sudo docker compose -f docker-compose.prod.yml up -d --build
```

## Segredos

Edite `deploy/.env.prod` na instância (ele está no `.gitignore`). Chaves de
LLM e Resend só entram lá — nunca no `application.properties`. Depois de
editar, rode o comando de atualização.

## HTTPS (opcional)

Precisa de um domínio. O caminho mais simples é apontar o domínio para o
Elastic IP e colocar um Caddy ou certbot na frente do Nginx; alternativa é
um Application Load Balancer com certificado do ACM.

## Backup do SQLite

`bash deploy/backup-db.sh <bucket>` copia o `petcuidar.db` para o S3. Dê à
instância uma IAM Role com `s3:PutObject` no bucket e agende no cron.
Não escale para mais de uma instância enquanto o banco for SQLite.

## Ativar o PostgreSQL do RDS e importar o volume Docker local

O perfil `postgres` foi testado com PostgreSQL 15. No RDS, use a mesma VPC e
região da EC2, mantenha o acesso público desativado e libere TCP 5432 no
security group do banco para o security group da EC2. A opção
**Connect to an EC2 compute resource** no console pode configurar essa
conexão. [Documentação da AWS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/ec2-rds-connect.html).

### Fazer uma cópia consistente no computador local

Na pasta do projeto, identifique o volume com `docker volume ls`. No
PowerShell, substitua `pet-cuidar_petcuidar-data` se o nome for diferente.
O comando usa a API de backup do SQLite com o volume original em modo
somente leitura, inclusive quando o SQLite está em modo WAL.

```powershell
$volume = 'pet-cuidar_petcuidar-data'
$backupDir = (New-Item -ItemType Directory -Force '.specs/features/postgresql-rds/private').FullName
docker run --rm --network none --mount "type=volume,source=$volume,target=/origem,readonly" --mount "type=bind,source=$backupDir,target=/backup" python:3.12-slim python -c "import sqlite3; s=sqlite3.connect('file:/origem/petcuidar.db?mode=ro', uri=True); integrity=s.execute('PRAGMA integrity_check').fetchone()[0]; assert integrity == 'ok'; assert s.execute('PRAGMA foreign_key_check').fetchone() is None; d=sqlite3.connect('/backup/petcuidar-volume-v7.db'); s.backup(d); assert d.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'; assert d.execute('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1').fetchone() == ('7', 1); d.close(); s.close()"
```

Transfira apenas a cópia aprovada para a EC2 por SCP, usando seu acesso SSH.
Guarde-a em uma pasta privada fora do repositório, como
`/home/ec2-user/petcuidar-migracao`. Mantenha o arquivo original e a cópia,
pois a base local e a base SQLite atual da EC2 podem conter registros diferentes.

### Preparar a conexão na EC2

Na pasta do projeto na EC2, baixe o certificado oficial e monte-o somente
para leitura. Use o endpoint DNS que aparece no console do RDS para que
`verify-full` valide também o nome do servidor.
[Certificados do RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).

```bash
mkdir -p deploy/rds
curl -fSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o deploy/rds/global-bundle.pem
```

Conecte-se a partir da EC2 com `psql` e o usuário principal. Consulte a senha
do administrador no Secrets Manager. O `psql` pede a senha interativamente.

```bash
read -r -p 'Endpoint do RDS: ' RDS_ENDPOINT
psql "host=$RDS_ENDPOINT port=5432 dbname=postgres user=postgres sslmode=verify-full sslrootcert=$PWD/deploy/rds/global-bundle.pem"
```

Crie um usuário dedicado e o banco vazio. Use uma senha exclusiva para a
aplicação. Se criou o banco `petcuidar` no console, execute
`ALTER DATABASE petcuidar OWNER TO petcuidar_app;` no lugar de `CREATE DATABASE`.

```sql
CREATE ROLE petcuidar_app LOGIN;
\password petcuidar_app
GRANT petcuidar_app TO postgres;
CREATE DATABASE petcuidar OWNER petcuidar_app;
```

Guarde uma cópia privada do `deploy/.env.prod` atual para voltar ao SQLite.
Edite o arquivo preservando as variáveis de autenticação e e-mail e substitua
as configurações do datasource pelas seguintes. Preencha os campos de
endpoint e senha direto no arquivo privado.

```dotenv
SPRING_PROFILES_ACTIVE=prod,postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://ENDPOINT_RDS:5432/petcuidar?sslmode=verify-full&sslrootcert=/run/rds/global-bundle.pem
SPRING_DATASOURCE_USERNAME=petcuidar_app
SPRING_DATASOURCE_PASSWORD='SENHA_DO_USUARIO_DA_APLICACAO'
```

Crie na EC2 o arquivo `docker-compose.postgres.yml` com o conteúdo abaixo.

```yaml
services:
  backend:
    volumes:
      - ./deploy/rds/global-bundle.pem:/run/rds/global-bundle.pem:ro
```

### Importar e ativar

Use o mesmo diretório e nome de projeto Compose do deploy em execução.
Se a instalação já usa `docker-compose.mailpit.yml`, inclua esse arquivo
na lista `compose`, depois de `docker-compose.prod.yml`. Confira se a cópia
SQLite transferida está no caminho do exemplo.

```bash
compose=(sudo docker compose -f docker-compose.prod.yml -f docker-compose.postgres.yml)
"${compose[@]}" build backend

# Durante a janela de migração, interrompa gravações no SQLite da EC2.
"${compose[@]}" stop backend
"${compose[@]}" run --rm --no-deps --entrypoint java \
  -v /home/ec2-user/petcuidar-migracao/petcuidar-volume-v7.db:/migration/petcuidar.db:ro \
  backend -jar /app/app.jar migrar-sqlite /migration/petcuidar.db UTC
```

Use `UTC` para o volume Docker local original do projeto. Para uma origem
gravada em outro fuso, use o fuso da JVM que gravou os dados. O importador
aceita uma cópia SQLite V7, cria o esquema pelo Flyway PostgreSQL e recusa
um destino com dados. Importe antes de subir a API, pois o bootstrap de
autenticação pode cadastrar a conta administrativa. As sessões e os
desafios antigos ficam fora da carga. Cada usuário precisará fazer login
novamente.

Compare as contagens do comando com as da cópia aprovada. Conecte-se ao banco
`petcuidar` com TLS, confirme as contagens de `consultas` e
`historico_atendimentos` e confira datas e horários conhecidos pelo ID.
Para a cópia ensaiada no desenvolvimento, o total foi de 99 registros de
negócio em 10 tabelas.

```sql
SELECT count(*) FROM consultas;
SELECT count(*) FROM historico_atendimentos;
SELECT id, data_hora FROM consultas WHERE id = <id_da_consulta>;
SELECT id, data, data_criacao, data_atualizacao
FROM historico_atendimentos WHERE id = <id_do_prontuario>;
```

Com a conferência concluída, suba os serviços com a mesma lista de arquivos.
Teste o login, a agenda e os prontuários antes de liberar o acesso.

```bash
"${compose[@]}" up -d
"${compose[@]}" ps
"${compose[@]}" logs --tail=100 backend
```

Nas próximas atualizações, inclua o mesmo overlay e execute
`"${compose[@]}" up -d --build` depois de atualizar o código. Para voltar
ao SQLite, interrompa gravações, restaure a cópia do `deploy/.env.prod`
anterior e suba o backend mantendo o volume original e os overlays anteriores
à migração. Se já houve gravações no RDS, preserve seus dados e reconcilie
essas mudanças antes da volta, pois elas não aparecem no SQLite da EC2.
Evite `docker compose down -v` durante todo o procedimento.


## Desligar para não gastar

Pare a instância quando não estiver usando (*Instance state → Stop*). Um
Elastic IP associado a instância parada é cobrado; libere-o se for
desligar de vez.
