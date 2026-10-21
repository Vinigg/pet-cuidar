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
     |
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

## Segredos (futuras chaves de API de IA)

Quando a integracao real com um provedor de IA (ex.: Anthropic ou OpenAI) for
implementada em `service/ia/*.java`, a chave de API **nao deve** ir no
`application.properties` do repositorio. Recomenda-se usar o **AWS Systems
Manager Parameter Store** (ou **Secrets Manager**) e injetar o valor como
variavel de ambiente no Elastic Beanstalk/EC2 no momento do deploy.

## Resumo dos custos (nivel MVP)

Todos os componentes sugeridos acima (EC2/Elastic Beanstalk t3.micro, S3,
CloudFront, volume EBS pequeno) cabem, no minimo nos primeiros meses, dentro
do **AWS Free Tier**, adequado ao volume de uso descrito no PRD (uma clinica,
poucos veterinarios, uso concentrado em horario comercial).
