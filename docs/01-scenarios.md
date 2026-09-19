# 01 — Cenários (CRUD)

## 1. Objetivo

Permitir criar, editar, duplicar e excluir cenários de simulação, persistidos por usuário, com validação específica por broker.

## 2. Conceito de mensageria estudado

Cada broker exige um parâmetro obrigatório diferente porque resolve escala/perda de forma diferente:

- **Kafka**: paralelismo é limitado por partições — um consumidor a mais que partições fica ocioso. Por isso `partitions` é obrigatório.
- **RabbitMQ**: fila tem limite opcional (`max-length`); ao encher, descarta as mensagens mais antigas. `queueCapacity = 0` significa "sem limite", não "inválido".
- **SQS**: mensagem que falha só reaparece após o `visibility timeout`. Sem esse valor não há como simular retry.

## 3. Decisões de design

- **Uma tabela, colunas nullable por broker.** `queue_capacity`, `partitions` e `visibility_timeout_seconds` vivem na mesma tabela `scenarios`, nunca em tabelas separadas por broker — o cenário muda de broker sem migração de dados. A obrigatoriedade de cada campo é regra de negócio (`ScenarioValidator`), não constraint de schema, porque depende do valor de outro campo (`broker`).
- **`ScenarioValidator` como classe própria, chamada pelo `ScenarioService`.** Single Responsibility: o service orquestra persistência e dono do recurso; o validator só decide se o payload é válido para aquele broker. Facilita testar as três regras isoladamente (`ScenarioValidatorTest`).
- **`updateEntity` do MapStruct em vez de recriar a entidade.** `create` e `update` reaproveitam a mesma assinatura (`ScenarioMapper.updateEntity(request, scenario)`), evitando duplicar 14 setters em dois lugares.
- **Duplicação copia campo a campo, não usa serialização.** `ScenarioService.duplicate` monta uma nova entidade explicitamente — mais verboso que um "clone genérico", mas evita carregar `id`/`createdAt`/`owner` errados por engano.
- **Frontend: campos condicionais no mesmo formulário, não telas separadas por broker.** Um único `ScenarioForm` com `broker === 'KAFKA' && <NumberField .../>` — reflete que é o mesmo cenário mudando de broker, não três formulários.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `domain/model/Scenario` | Entidade JPA — schema, sem lógica de negócio | `User` (owner) |
| `service/scenario/ScenarioValidator` | Regra obrigatória por broker | `ScenarioRequest` |
| `service/scenario/ScenarioService` | CRUD + duplicação, sempre escopado ao dono | `ScenarioRepository`, `UserRepository`, `ScenarioMapper`, `ScenarioValidator` |
| `controller/ScenarioController` | Rotas `/api/scenarios/*` | `ScenarioService` |
| `api/modules/scenario.service.ts` | Único ponto de chamada HTTP do domínio de cenários no frontend | `api` central |
| `pages/ScenariosPage/ScenarioForm.tsx` | Formulário RHF com campos condicionais por broker | `useScenarioForm`, `scenarioFieldTips` |
| `pages/ScenariosPage/ScenarioList.tsx` | Lista lateral — selecionar, duplicar, excluir | `Scenario[]` |
| `pages/ScenariosPage/scenarioFieldTips.ts` | Textos de tooltip copiados do protótipo | — |

## 5. Fluxo de uma requisição

`POST /api/scenarios` (criar):

1. `ScenarioController.create` recebe `ScenarioRequest`, valida com Bean Validation (`@Min`, `@NotNull`, etc. — regras genéricas).
2. `ScenarioService.create` chama `ScenarioValidator.validate` (regra específica do broker) antes de tocar no banco.
3. Busca o `User` autenticado, cria a entidade, salva, retorna `ScenarioResponse` via `ScenarioMapper`.

Frontend (`ScenariosPage → useScenarioForm → ScenarioService`):

1. `ScenariosPage` carrega a lista uma vez (efeito com cleanup, sem re-fetch a cada seleção).
2. Selecionar um cenário passa a entidade para `ScenarioForm`; `useScenarioForm` reresseta o RHF com `form.reset` quando a seleção muda.
3. Submit chama `ScenarioService.create` ou `.update` dependendo se há cenário selecionado; em caso de sucesso, `ScenariosPage.refresh` recarrega a lista e mantém o item salvo selecionado.

## 6. Trechos comentados

```java
// ScenarioValidator.java — por que switch sem default:
// BrokerType é um enum fechado (KAFKA, RABBITMQ, SQS); um novo valor
// quebra a compilação aqui em vez de passar validação nenhuma (fail-fast).
public void validate(ScenarioRequest request) {
    switch (request.broker()) {
        case KAFKA -> requirePositive(request.partitions(), "partitions", BrokerType.KAFKA);
        case RABBITMQ -> requireNonNegative(request.queueCapacity(), "queueCapacity", BrokerType.RABBITMQ);
        case SQS -> requirePositive(request.visibilityTimeoutSeconds(), "visibilityTimeoutSeconds", BrokerType.SQS);
    }
}
```

```tsx
// ScenariosPage.tsx — por que o fetch inicial não usa a função `refresh`
// reutilizável: linters de efeito (react-hooks) sinalizam setState
// disparado a partir de uma closure externa chamada dentro do efeito.
// Escrever o fetch inline, com uma flag `cancelled`, deixa explícito
// que o efeito só atualiza estado enquanto o componente segue montado.
useEffect(() => {
  let cancelled = false;
  ScenarioService.list()
    .then((list) => {
      if (cancelled) return;
      setScenarios(list);
      setSelectedId((current) => current ?? list[0]?.id ?? null);
    })
    .catch((err) => { if (!cancelled) setError(...); });
  return () => { cancelled = true; };
}, []);
```

## 7. Como testar manualmente

```sh
TOKEN=$(curl -s -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}' | jq -r .token)

curl -X POST http://localhost:1337/api/scenarios \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Kafka healthy","broker":"KAFKA","ratePerSecond":200,"consumers":4,"processingMs":15,"failurePct":1,"maxRetries":3,"messageSizeKb":2,"durationSeconds":120,"partitions":6,"dlqEnabled":true,"burstEnabled":false}'

# sem "partitions" deve retornar 400
curl -X POST http://localhost:1337/api/scenarios \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"bad","broker":"KAFKA", ...}'
```

Na UI: `/` (após login) → criar cenário, trocar broker e confirmar que o campo condicional muda (Partições ↔ Capacidade da fila ↔ Visibility timeout), duplicar, excluir.

## 8. O que eu aprendi / erros cometidos

- Testado ponta a ponta no navegador real (Chrome via automação): criar, trocar broker com re-render do campo condicional, duplicar e excluir — os quatro fluxos funcionaram sem ajuste no primeiro teste.
- O linter `eslint-plugin-react-hooks` (parte do React Compiler) rejeita `setState` disparado a partir de uma função `useCallback` externa chamada dentro de um `useEffect`, mesmo quando o `setState` real só roda depois de um `await`. A correção não é suprimir a regra — é escrever o fetch inicial inline no efeito com uma flag de cancelamento, que é o padrão que a própria mensagem de erro recomenda.
- MapStruct com `@MappingTarget` elimina a necessidade de dois métodos de mapeamento (create/update) quase idênticos.
