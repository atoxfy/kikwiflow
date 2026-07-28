# 📖 Guia de Integração: Busca Avançada de Processos (Kikwiflow)

Este guia detalha como utilizar o endpoint de busca avançada de instâncias de instâncias. A API foi desenhada para ser altamente flexível, permitindo cruzamento de dados, paginação, ordenação e buscas profundas dentro das variáveis do processo.

### 🔗 Endpoint
* **URL:** `POST /api/v1/process-instances/search` *(Ajuste para o path real da sua aplicação)*
* **Content-Type:** `application/json`

---

### 💡 Exemplo de Payload (Request Body)

Nenhum campo é obrigatório. Você pode enviar apenas os filtros que desejar. Os filtros enviados serão combinados com o operador lógico `AND`.

```json
{
  "processDefinitionKeys": ["intermediacao-veiculos"],
  "activeNodeId": "AGUARDANDO_CONTATO",
  "tenantIds": ["empresa-abc-123", "empresa-xyz-789"],
  "statuses": ["ACTIVE"],
  "startedAfter": "2026-07-01T00:00:00.000Z",
  "startedBefore": "2026-07-31T23:59:59.999Z",
  "variables": {
    "prospectType": "VENDA",
    "marcaVeiculo": "Toyota"
  },
  "variablesExist": ["telefoneContato"],
  "orderBy": "startedAt",
  "ascending": false,
  "page": 0,
  "size": 20
}
```

---

### 🎛️ Dicionário de Parâmetros

#### 1. Filtros de Definição e Estado
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `processDefinitionId` | `String` | Busca pelo ID exato e versão do processo. |
| `processDefinitionIds` | `Array<String>` | Lista de IDs de definição para buscar múltiplos processos. |
| `processDefinitionKeys` | `Array<String>` | **(Recomendado)** Busca por chaves do processo (ex: `["intermediacao-veiculos"]`), ignorando a versão. A API traduzirá automaticamente para as versões corretas. |
| `activeNodeId` | `String` | Retorna apenas instâncias que possuam tarefas ativas ou aguardando nesta exata etapa (ex: `"EM_ESTOQUE"`). |
| `statuses` | `Array<String>` | Filtra pelo status do processo. Valores possíveis: `ACTIVE`, `COMPLETED`, `SUSPENDED`, `CANCELED`. |

#### 2. Filtros de Negócio e Organização
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `tenantId` | `String` | ID da empresa/inquilino dono do processo. |
| `tenantIds` | `Array<String>` | Permite buscar processos de mais de uma empresa simultaneamente. |
| `businessKey` | `String` | Código identificador único do negócio (ex: número da Deal). |
| `businessKeys` | `Array<String>` | Lista de códigos identificadores. |

#### 3. Filtros Temporais (Datas)
> **Aviso:** Todas as datas devem ser enviadas no formato absoluto ISO-8601 (UTC), contendo o sufixo `Z`.

| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `startedAfter` | `String` | Instâncias iniciadas a partir desta data/hora (Inclusivo). |
| `startedBefore` | `String` | Instâncias iniciadas até esta data/hora (Inclusivo). |

#### 4. Filtros Avançados de Variáveis 
A busca dentro das variáveis é feita de forma dinâmica, sem precisar criar colunas novas no banco de dados.

| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `variables` | `Object` | Mapeamento chave-valor de variáveis que devem bater **exatamente** com o que está no processo. Ex: `{ "prospectType": "VENDA", "statusCredito": "APROVADO" }` |
| `variablesExist` | `Array<String>` | Retorna processos que *possuem* essa variável preenchida, independentemente do valor dela. Ex: `["cpfComprador"]`. |

#### 5. Ordenação e Paginação
| Parâmetro | Tipo | Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| `orderBy` | `String` | `"startedAt"` | Campo de ordenação base. (ex: `"endedAt"`, `"businessKey"`). |
| `ascending` | `Boolean` | `false` | `true` para crescente, `false` para decrescente (do mais recente para o mais antigo). |
| `page` | `Number` | `0` | Índice da página (começa em `0`). |
| `size` | `Number` | `20` | Quantidade de itens por página (máximo `100`). |

---

### 📦 Formato de Resposta (Response)
A API retornará um objeto paginado contendo um resumo (Summary) das instâncias encontradas.

```json
{
  "content": [
    {
      "id": "ae8fd836-c6a8-4dd1-af8d-0e6ec3530657",
      "businessKey": "100234",
      "status": "ACTIVE",
      "processDefinitionId": "fc451f6f-a944-4dbb-b2ba-b5fb6433f40a",
      "startedAt": "2026-07-24T17:10:31.460Z",
      "endedAt": null,
      "activeNodes": {
        "AGUARDANDO_CONTATO": 1
      }
    }
  ],
  "totalElements": 45,
  "totalPages": 3,
  "page": 0,
  "size": 20
}
```