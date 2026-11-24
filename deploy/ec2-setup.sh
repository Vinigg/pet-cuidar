#!/bin/bash
# Prepara uma EC2 Amazon Linux 2023 e sobe o Pet Cuidar.
# Uso (na instancia):  curl -fsSL https://raw.githubusercontent.com/Vinigg/pet-cuidar/main/deploy/ec2-setup.sh | sudo bash
# Ou cole este arquivo no campo "User data" ao criar a instancia.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/Vinigg/pet-cuidar.git}"
BRANCH="${BRANCH:-main}"
APP_DIR=/opt/pet-cuidar

# 1) Docker + Compose + Git
dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user || true
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 2) Swap de 2 GB (Spring + Python + build do Maven nao cabem folgados em 1 GB)
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
fi

# 3) Codigo
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" fetch origin "$BRANCH" && git -C "$APP_DIR" checkout "$BRANCH" && git -C "$APP_DIR" pull
else
  git clone -b "$BRANCH" "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

# 4) Variaveis de ambiente (preenche a origem com o IP publico da instancia)
if [ ! -f deploy/.env.prod ]; then
  cp deploy/.env.prod.example deploy/.env.prod
  TOKEN=$(curl -s -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4 || true)
  [ -n "$PUBLIC_IP" ] && sed -i "s#http://SEU_IP_OU_DOMINIO#http://$PUBLIC_IP#" deploy/.env.prod
fi

# 5) Sobe tudo
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
