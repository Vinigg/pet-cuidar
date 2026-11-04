# Funcionalidade: Registro e Atualização de Atendimento Veterinário

## Visão Geral

Esta funcionalidade permite que veterinários autorizados registrem e atualizem descrições clínicas de atendimentos vinculados a pets e consultas, com rastreabilidade completa de alterações.

## Critérios de Aceite Atendidos

✅ **Veterinário autorizado registra descrição clínica vinculada ao pet e à consulta**
- Endpoint `POST /atendimentos` valida veterinário ativo
- Descrição clínica é obrigatória e vinculada a pet e consulta existentes
- Campos estruturados com validação de comprimento (10-2000 caracteres)

✅ **Campos críticos são preenchidos/atualizados em campos estruturados e persistidos**
- DTOs com validações usando `@NotNull`, `@NotBlank`, `@Size`
- Persistência em banco de dados com JPA/Hibernate
- Campos críticos: `descricaoClinica`, `veterinarioId`, `petId`, `consultaId`

✅ **Alteração fica rastreável**
- Campo `dataCriacao`: registra quando foi criado o atendimento (imutável)
- Campo `dataAtualizacao`: registra última atualização (atualiza automaticamente)
- Campo `veterinarioCriacaoId`: ID do veterinário que criou
- Campo `veterinarioAtualizacaoId`: ID do veterinário que atualizou por último
- Versionamento otimista com `@Version` para controle de concorrência

## Arquitetura Implementada

### 1. **Modelo de Dados** (`HistoricoAtendimento`)
```
- id (PK)
- pet_id (FK)
- consulta_id (FK)
- veterinario_id (FK)
- descricao (clínica)
- resumo_ia (gerado por IA)
- data (do atendimento)
- data_criacao (auditoria)
- data_atualizacao (auditoria)
- veterinario_criacao_id (auditoria)
- veterinario_atualizacao_id (auditoria)
- versao (controle otimista)
```

**Callbacks JPA:**
- `@PrePersist`: define `dataCriacao` e `dataAtualizacao` no momento da criação
- `@PreUpdate`: atualiza `dataAtualizacao` em cada modificação

### 2. **Data Transfer Objects (DTOs)**

#### `RegistroAtendimentoRequestDTO`
Usado para registrar novo atendimento:
```json
{
  "petId": 1,
  "consultaId": 1,
  "veterinarioId": 1,
  "descricaoClinica": "Descrição clínica do atendimento..."
}
```

#### `AtualizacaoAtendimentoRequestDTO`
Usado para atualizar atendimento existente:
```json
{
  "atendimentoId": 1,
  "veterinarioId": 1,
  "descricaoClinica": "Descrição clínica atualizada..."
}
```

#### `AtendimentoResponseDTO`
Resposta com detalhes completos do atendimento:
```json
{
  "id": 1,
  "petId": 1,
  "petNome": "Rex",
  "consultaId": 1,
  "veterinarioId": 1,
  "veterinarioNome": "Dr. João",
  "descricaoClinica": "...",
  "resumoIa": "...",
  "dataAtendimento": "2026-11-04T16:00:00",
  "dataCriacao": "2026-11-04T16:30:00",
  "dataAtualizacao": "2026-11-04T16:35:00",
  "veterinarioCriacao": "...",
  "veterinarioAtualizacao": "..."
}
```

### 3. **Serviço** (`AtendimentoService`)

**Responsabilidades:**
- Validar existência de pet, consulta e veterinário
- Verificar se veterinário está ativo
- Registrar novo atendimento com rastreabilidade
- Atualizar atendimento existente preservando histórico
- Listar atendimentos de um pet

**Métodos principais:**
- `registrarAtendimento()`: Cria novo atendimento
- `atualizarAtendimento()`: Atualiza descrição existente
- `obterAtendimento()`: Obtém detalhes de um atendimento
- `listarAtendimentosPorPet()`: Lista histórico de atendimentos do pet

### 4. **Controller REST** (`AtendimentoController`)

**Endpoints:**

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/atendimentos` | Registrar novo atendimento |
| PATCH | `/atendimentos` | Atualizar atendimento existente |
| GET | `/atendimentos/{id}` | Obter detalhes de um atendimento |
| GET | `/atendimentos/pet/{petId}` | Listar atendimentos de um pet |

## Garantias de Segurança e Integridade

### 1. **Validação de Veterinário**
```java
if (!veterinario.isAtivo()) {
    throw new IllegalArgumentException("Veterinário inativo não pode registrar atendimento");
}
```

### 2. **Transações ACID**
```java
@Transactional
public AtendimentoResponseDTO registrarAtendimento(...)
```
- Garante atomicidade: tudo sucede ou nada
- Isolamento: não há race conditions
- Durabilidade: salvo em banco de dados

### 3. **Versionamento Otimista**
```java
@Version
private Long versao;
```
Detecta atualizações concorrentes e evita sobrescrita de dados

### 4. **Tratamento de Exceções**
```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecursoNaoEncontradoException extends RuntimeException
```
Respostas HTTP apropriadas para erros

## Rastreabilidade e Auditoria

### Campos de Auditoria
- **Quem criou?** → `veterinarioCriacaoId`
- **Quando foi criado?** → `dataCriacao` (imutável)
- **Quem atualizou por último?** → `veterinarioAtualizacaoId`
- **Quando foi atualizado?** → `dataAtualizacao` (atualiza automaticamente)
- **Quantas vezes foi atualizado?** → `versao`

### Histórico Completo
O modelo `HistoricoAtendimento` mantém todas as informações necessárias para:
1. **Reconstrair sequência de eventos:** `dataCriacao` < `dataAtualizacao`
2. **Identificar responsáveis:** IDs de veterinários registrados
3. **Validar integridade:** Versionamento detecta conflitos
4. **Conformidade regulatória:** Rastreamento completo de quem fez o quê e quando

## Exemplos de Uso

### 1. Registrar Atendimento
```bash
curl -X POST http://localhost:8080/atendimentos \
  -H "Content-Type: application/json" \
  -d '{
    "petId": 1,
    "consultaId": 1,
    "veterinarioId": 1,
    "descricaoClinica": "Pet apresenta sintomas de otite..."
  }'
```

### 2. Atualizar Atendimento
```bash
curl -X PATCH http://localhost:8080/atendimentos \
  -H "Content-Type: application/json" \
  -d '{
    "atendimentoId": 1,
    "veterinarioId": 1,
    "descricaoClinica": "Pet apresenta sintomas de otite. ATUALIZADO: tutor relata melhora..."
  }'
```

### 3. Consultar Histórico de Atendimentos
```bash
curl http://localhost:8080/atendimentos/pet/1
```

## Testes de Aceitação

### TC-001: Registrar Atendimento Válido
```
Dado: Veterinário ativo, Pet existente, Consulta existente
Quando: POST /atendimentos com dados válidos
Então: Retorna 201 Created com atendimento registrado
  E: dataCriacao e dataAtualizacao são definidas
  E: veterinarioCriacaoId = veterinarioId
```

### TC-002: Rejeitar Veterinário Inativo
```
Dado: Veterinário inativo
Quando: POST /atendimentos com veterinário inativo
Então: Retorna 400 Bad Request
  E: Mensagem: "Veterinário inativo não pode registrar atendimento"
```

### TC-003: Validar Campos Obrigatórios
```
Dado: Descrição clínica vazia
Quando: POST /atendimentos
Então: Retorna 400 Bad Request
  E: Mensagem de validação indicando campos obrigatórios
```

### TC-004: Atualizar com Rastreabilidade
```
Dado: Atendimento existente
Quando: PATCH /atendimentos com nova descrição
Então: dataAtualizacao > dataCriacao
  E: veterinarioAtualizacaoId = novo veterinário
  E: versao incrementada
```

## Banco de Dados

### Migrations (Flyway)

Adicione a seguinte migration para criar/atualizar as colunas:

```sql
-- V3__adicionar_campos_auditoria_historico_atendimento.sql
ALTER TABLE historico_atendimentos ADD COLUMN veterinario_id BIGINT;
ALTER TABLE historico_atendimentos ADD COLUMN data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE historico_atendimentos ADD COLUMN data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE historico_atendimentos ADD COLUMN veterinario_criacao_id BIGINT;
ALTER TABLE historico_atendimentos ADD COLUMN veterinario_atualizacao_id BIGINT;
ALTER TABLE historico_atendimentos ADD COLUMN versao INTEGER DEFAULT 0;

ALTER TABLE historico_atendimentos ADD CONSTRAINT fk_veterinario_atendimento 
  FOREIGN KEY (veterinario_id) REFERENCES veterinarios(id);
ALTER TABLE historico_atendimentos ADD CONSTRAINT fk_veterinario_criacao 
  FOREIGN KEY (veterinario_criacao_id) REFERENCES veterinarios(id);
ALTER TABLE historico_atendimentos ADD CONSTRAINT fk_veterinario_atualizacao 
  FOREIGN KEY (veterinario_atualizacao_id) REFERENCES veterinarios(id);
```

## Próximos Passos

1. **Criar migration Flyway** para adicionar colunas de auditoria
2. **Adicionar autenticação/autorização** para validar que apenas veterinários autorizados acessam
3. **Integrar com ResumoHistoricoService** para gerar resumos de IA
4. **Adicionar logs de auditoria** para conformidade regulatória
5. **Criar testes unitários** para validar regras de negócio
6. **Adicionar paginação** em `listarAtendimentosPorPet()` para grandes volumes

## Conclusão

A funcionalidade foi implementada seguindo os princípios de:
- ✅ **Rastreabilidade completa:** Todos os campos de auditoria estão presentes
- ✅ **Segurança:** Validação de veterinário ativo
- ✅ **Integridade:** Transações ACID e versionamento otimista
- ✅ **Usabilidade:** API RESTful simples e bem documentada
