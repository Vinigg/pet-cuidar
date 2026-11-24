# Deploy na AWS — EC2 única com Docker Compose

Tudo roda numa só instância: Nginx (front React + proxy da API), back-end
Spring Boot (SQLite num volume) e o microserviço Python de no-show.

```
Navegador ──► EC2 :80  Nginx ──┬─ /        → build do React
                               └─ /api/*   → backend:8080 ──► predictor:8000
                                                  │
                                          volume petcuidar-data (petcuidar.db)
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

## Desligar para não gastar

Pare a instância quando não estiver usando (*Instance state → Stop*). Um
Elastic IP associado a instância parada é cobrado; libere-o se for
desligar de vez.
