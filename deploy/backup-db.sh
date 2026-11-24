#!/bin/bash
# Copia o petcuidar.db para um bucket S3. Agende no cron da EC2:
#   0 3 * * * bash /opt/pet-cuidar/deploy/backup-db.sh meu-bucket-backup
# A instancia precisa de uma IAM Role com s3:PutObject nesse bucket.
set -euo pipefail
BUCKET="$1"
STAMP=$(date +%Y%m%d-%H%M)
VOL=$(docker volume inspect -f '{{ .Mountpoint }}' pet-cuidar_petcuidar-data)
cp "$VOL/petcuidar.db" "/tmp/petcuidar-$STAMP.db"
aws s3 cp "/tmp/petcuidar-$STAMP.db" "s3://$BUCKET/backups/petcuidar-$STAMP.db"
rm -f "/tmp/petcuidar-$STAMP.db"
