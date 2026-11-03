# Detalhamento da consulta

Permite abrir uma consulta do painel do dia e ver, num modal, os dados da
consulta, do pet, do tutor e do veterinário. A tela é somente leitura.

## API

`GET /consultas/{id}`

| Parâmetro | Onde | Tipo | Descrição |
| --- | --- | --- | --- |
| `id` | caminho | inteiro positivo | Identificador da consulta |

Resposta `200 OK`:

```json
{
  "id": 1,
  "dataHora": "2026-11-23T10:30:00",
  "status": "AGENDADA",
  "pet": {
    "id": 1, "nome": "Rex", "especie": "Cachorro", "idade": 3,
    "alergiasCriticas": "Dipirona"
  },
  "tutor": {
    "id": 1, "nome": "Arthur Abelardo",
    "email": "arthur@example.com", "telefone": "85999999999"
  },
  "veterinario": { "id": 2, "nome": "Dra. Camila Moraes", "especialidade": "Dermatologia" }
}
```

- `dataHora` segue o formato já usado pela API (data e hora locais da clínica,
  sem fuso). A tela separa a data (`dd/MM/yyyy`) e o horário (`HH:mm`).
- `status` é um dos valores de `StatusConsulta` (ver `consulta-estados.md`).
- `pet.especie`, `pet.idade`, `pet.alergiasCriticas`, `tutor.telefone` e
  `veterinario.especialidade` são opcionais no cadastro e podem vir `null`.

| Situação | HTTP |
| --- | --- |
| Consulta encontrada | 200 |
| `id` não numérico (ex.: `abc`), zero ou negativo | 400 |
| Consulta inexistente | 404 |

Erros seguem o formato padrão da PC-010:
`{"status":404,"mensagem":"Consulta nao encontrada: id 99","timestamp":"..."}`.

### Implementação

- `ConsultaController.buscarDetalhe` → `ConsultaService.buscarDetalhe`
  (`@Transactional(readOnly = true)`) → `ConsultaRepository.buscarDetalhe`.
- A consulta usa `join fetch` em pet, tutor e veterinário: um único SELECT,
  sem N+1 nem carregamento lazy fora da transação (coberto por teste que
  conta os comandos SQL).
- A resposta é o DTO `ConsultaDetalheDTO`, nunca a entidade JPA.
- O endpoint não altera nada: não recalcula predição, não gera lembrete e não
  muda o status.
- **Ficam de fora de propósito:** o CPF e a lista de pets do tutor, e a versão
  de bloqueio otimista do veterinário. A tela não precisa desses dados.

## Front-end

- No **Painel do dia**, cada linha tem a ação **Ver detalhes**, que abre o
  componente `DetalhesConsulta` (modal) com o `id` real da consulta.
- O modal chama `obterConsulta(id)` (`src/api/api.js`), ou seja, só
  `GET /consultas/{id}`. A agenda não é recarregada.
- Estados:
  - carregando;
  - dados em quatro seções (Consulta, Pet, Tutor, Veterinário);
  - 404, com a mensagem "Consulta nao encontrada";
  - falha de comunicação, com o botão "Tentar novamente".
- O status aparece como badge colorido, no mesmo estilo dos badges de risco.
  As alergias críticas aparecem em destaque, e campos vazios aparecem como
  "Nao informado".
- Fecha pelo botão **Fechar**, pela tecla **Esc** ou clicando fora do painel.
  O foco volta para o botão que abriu o modal.
- Em telas estreitas as seções ficam em uma coluna, e a tabela do painel ganha
  rolagem horizontal.

## Limitações do modelo atual

Os campos abaixo foram citados como referência, mas **não existem nas
entidades**. Por isso não foram inventados:

- **Pet:** raça, sexo e data de nascimento. Só existe `idade` em anos.
- **Consulta:** observações, motivo ou tipo de atendimento.
- **Veterinário:** CRMV e contato.

Para exibir qualquer um deles é preciso definir o campo no modelo, migrar o
banco, ajustar o cadastro e só então incluí-lo no DTO e na tela.

O lembrete e a predição de no-show não fazem parte do detalhamento. A
predição tem endpoint próprio, que recalcula e grava o score, e não deve ser
chamada pela visualização.
