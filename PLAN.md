# Plano de Projeto — Simulador de Mensageria

Baseado no protótipo `Simulador de Mensageria.dc.html` e nas regras do **BIMD Template** (`uploads/Main ReadMe.md`).

---

## 0. Decisões de stack (registrar em `ARCHITECTURE.md`)

| Decisão | Escolha | Justificativa (critérios do README) |
|---|---|---|
| Backend | **Java 21 / Spring Boot 3** | Regras de negócio complexas (motor de simulação, modelo analítico); API de carga (rodar N ticks) |
| Banco | **PostgreSQL** | Relações fixas (Scenario → SimulationRun → Tick/Event); relatórios e queries analíticas sobre execuções |
| ORM | `spring-boot-starter-data-jpa` + Hibernate, `DDL_AUTO=validate` + Flyway para migrations | Exigência do template |
| Frontend | React + TypeScript + Vite + Ant Design + React Hook Form + Dayjs | Exigência do template |
| Gráficos | `@ant-design/plots` (mesmo ecossistema do AntD) | Sem CSS próprio, tokens via `theme.ts` |
| Brokers reais | Kafka e RabbitMQ em Docker; SQS via **LocalStack** | Testar os três localmente sem conta AWS; adapters isolados por porta (DIP) |
| Live run | **SSE** (`text/event-stream`) via `SseEmitter` | Simulação ao vivo sem WebSocket (README marca WS como ⚠️ em Java) |
| Estado global | React Context (`useAuth`, `useTheme`) — sem Redux | README deixa livre; escopo pequeno |

O que **vem do template e é reaproveitado sem alteração**: autenticação JWT (rotas `/api/auth/*`, `/api/profile/*`), storage S3/MinIO, Mailpit, `theme.ts` claro/escuro, `docker-compose.dev.yml`.

---

## 1. Domínio

### Entidades (PostgreSQL)

```
users                (do template)
scenarios            id, owner_id → users, name, broker ENUM(KAFKA, RABBITMQ, SQS),
                     rate_per_second, consumers, processing_ms, failure_pct, max_retries,
                     message_size_kb, duration_seconds, queue_capacity, partitions,
                     visibility_timeout_seconds, dlq_enabled, burst_enabled,
                     created_at, updated_at (ISO 8601)
simulation_runs      id, scenario_id → scenarios, status ENUM(RUNNING, STOPPED, COMPLETED),
                     mode ENUM(LIVE, INSTANT), seed, started_at, finished_at,
                     produced_total, delivered_total, dlq_total, dropped_total, retries_total
simulation_ticks     id, run_id → simulation_runs, second, produced, consumed, failed, dropped,
                     backlog, utilization, p50_ms, p95_ms, p99_ms, capacity
simulation_events    id, run_id, second, type ENUM(SATURATION, QUEUE_FULL, FIRST_DLQ, LAG,
                     BURST_START, BURST_END, RECOVERED, FINISHED), message
```

Parâmetros específicos por broker (`partitions`, `queue_capacity`, `visibility_timeout_seconds`) ficam na mesma tabela como colunas nullable; a validação de qual é obrigatório fica no `ScenarioValidator` (service), não na entidade.

### Regras de negócio (porta do protótipo → Java)

| Conceito no protótipo | Classe Java | Camada |
|---|---|---|
| `step(sim, sc)` | `SimulationEngine` + `BrokerBehavior` (interface) com `KafkaBehavior`, `RabbitMqBehavior`, `SqsBehavior` | service |
| `newSim` / estado do tick | `SimulationState` (mutável, interno ao engine) | service |
| `model(sc, broker)` | `AnalyticalQueueModel` (M/M/c simplificado) | service |
| `tradeoffs(sc)` | `BrokerTradeoffService` (pontuação 35/25/20/10/10 em constantes nomeadas) | service |
| `cost(sc, sim)` | `CostEstimator` + `BrokerPricing` (interface, uma impl por broker) | service |
| `narrative` / `conclusion` / `insights` | `ReportNarrativeService` | service |
| detecção de eventos | `EventDetector` (uma classe por tipo, OCP) | service |

`BrokerBehavior` é o ponto de extensão (Open/Closed): adicionar um broker novo = nova implementação, nenhuma alteração no engine.

---

## 2. Contrato da API (`/api`, JWT obrigatório exceto auth)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/scenarios` | Lista cenários do usuário |
| POST | `/scenarios` | Cria (`ScenarioRequest` com Bean Validation) |
| GET / PUT / DELETE | `/scenarios/{id}` | Detalhe, edição, exclusão |
| POST | `/scenarios/{id}/duplicate` | Cópia com sufixo "(cópia)" |
| POST | `/scenarios/{id}/runs?mode=INSTANT` | Roda tudo, retorna `RunSummaryResponse` |
| POST | `/scenarios/{id}/runs?mode=LIVE` | Cria run RUNNING, retorna `runId` |
| GET | `/runs/{runId}/stream` | SSE: um `TickResponse` por tick + eventos + `finished` |
| POST | `/runs/{runId}/stop` | Interrompe |
| GET | `/runs/{runId}` | Sumário + KPIs |
| GET | `/runs/{runId}/ticks` | Série completa para os gráficos |
| GET | `/runs/{runId}/events` | Timeline |
| GET | `/runs/{runId}/report` | Narrativa + insights + conclusão |
| GET | `/scenarios/{id}/tradeoffs` | Modelo analítico dos 3 brokers (não depende de run) |
| GET | `/runs/compare?a={runId}&b={runId}` | Duas séries alinhadas para o modo lado a lado |
| POST | `/scenarios/{id}/runs?mode=LIVE&execution=REAL` | Fase 8: roda contra o broker real do cenário |
| GET | `/brokers/health` | Fase 8: status de Kafka, RabbitMQ e LocalStack locais |

Datas sempre ISO 8601 string. Controllers só retornam DTOs (MapStruct).

---

## 3. Fases

Cada fase termina com PR para `develop`, `./mvnw verify` e `npm run lint && npm run build` verdes. Estimativas em dias úteis para uma pessoa.

### Fase 0 — Fundação (2 dias)
- Clonar o template; remover backend Node; manter Java + PostgreSQL.
- Renomear pacote `com.bimd.template` → `com.bimd.msgsim`.
- Adicionar Flyway; migration `V1__template_users.sql`.
- `ARCHITECTURE.md` com as decisões da seção 0.
- `docker compose up postgres minio mailpit`; login com usuário inicial funcionando.
- **Entrega:** repositório sobe, login funciona, CI local verde.
- Doc: `docs/00-foundation.md` — como o template sobe, o que é JWT stateless, por que Flyway + `ddl-auto=validate`.
- Branch: `chore/project-bootstrap`

### Fase 1 — Cenários (CRUD) (3 dias)
Backend
- `Scenario` entity, `ScenarioRepository`, `ScenarioService`, `ScenarioController`, DTOs + `ScenarioMapper`.
- `ScenarioValidator`: regras por broker (Kafka exige `partitions ≥ 1`; RabbitMQ `queue_capacity ≥ 0`; SQS `visibility_timeout ≥ 1`).
- Testes: unitário do validator; MockMvc do controller.

Frontend
- `scenario.service.ts`, tipos em `types.ts`.
- `ScenariosPage/` com `ScenarioList`, `ScenarioForm` (RHF + `Controller`, campos condicionais por broker), `useScenarioForm.ts`.
- Tooltips "?" em cada campo (`Tooltip` do AntD) com os textos do protótipo.
- **Entrega:** criar, editar, duplicar, excluir cenários persistidos por usuário.
- Doc: `docs/01-scenarios.md` — o que cada parâmetro significa em mensageria; fluxo Controller→Service→Repository; por que a validação por broker vive no service.
- Branches: `feat/scenario-crud-api`, `feat/scenario-crud-ui`

### Fase 2 — Motor de simulação (instantâneo) (4 dias)
- `SimulationEngine`, `SimulationState`, `BrokerBehavior` + 3 implementações, `EventDetector`s, `LatencyEstimator`.
- Seed determinístico (`java.util.Random(seed)`) para reproduzir execuções.
- `SimulationRun`, `SimulationTick`, `SimulationEvent` + repositories; `SimulationRunService` grava em lote (`saveAll`).
- Endpoint `POST /scenarios/{id}/runs?mode=INSTANT`, `GET /runs/{id}`, `/ticks`, `/events`.
- Testes unitários com seed fixo: carga > capacidade ⇒ backlog cresce; Rabbit com limite ⇒ `dropped > 0`; Kafka `consumers > partitions` ⇒ capacidade limitada; SQS retry só após visibility timeout.
- **Entrega:** rodar um cenário e obter séries + eventos via API.
- Doc: `docs/02-simulation-engine.md` — anatomia de um tick; capacidade = consumidores × 1000 ÷ ms; diferenças Kafka/RabbitMQ/SQS lado a lado; ADRs 0003 e 0004.
- Branch: `feat/simulation-engine`

### Fase 3 — Dashboard de resultados (4 dias)
- `run.service.ts`; `ReportPage/` com `KpiGrid`, `ThroughputChart`, `BacklogChart`, `LatencyChart`, `ConsumerUtilization`, `MessageOutcomeBar`, `EventTimeline`.
- Componentes genéricos em `components/ui/` (`StatCard`, `HelpLabel` = label + ícone "?" com Tooltip).
- Cores semânticas (`semantic.ok / warning / danger / info`) em `theme.ts`; nada de hex solto.
- Botão "Resultado instantâneo" + barra de progresso.
- **Entrega:** relatório completo de uma execução, claro/escuro.
- Doc: `docs/03-report-dashboard.md` — como ler cada gráfico (backlog, p50/p95/p99, utilização); por que percentis e não média.
- Branch: `feat/report-dashboard`

### Fase 4 — Execução ao vivo (3 dias)
- `LiveSimulationService`: `@Async` + `SseEmitter`, registra emitters por `runId`, emite tick a tick com velocidade configurável (`?speed=1|5|20`), `stop` encerra.
- Persistência incremental a cada N ticks (constante `TICK_FLUSH_BATCH`).
- Frontend: `useLiveRun.ts` (`EventSource`, reconexão, cleanup no unmount); gráficos atualizam a cada evento.
- **Entrega:** "Rodar ao vivo", "Parar", progresso em tempo real.
- Doc: `docs/04-live-simulation.md` — SSE vs WebSocket vs polling; ciclo de vida do `SseEmitter`; ADR 0002.
- Branch: `feat/live-simulation-sse`

### Fase 5 — Relatório analítico e trade-offs (3 dias)
- `AnalyticalQueueModel`, `BrokerTradeoffService`, `CostEstimator`/`BrokerPricing`, `ReportNarrativeService` (narrativa, insights, conclusão, `mainDelta`).
- Endpoints `/tradeoffs` e `/runs/{id}/report`.
- Frontend: `TradeoffCards`, `NarrativePanel`, `ConclusionPanel`.
- Testes: modelo estável quando `ratio < 1`; melhor broker muda quando `consumers > partitions`; texto de conclusão cita o broker vencedor.
- **Entrega:** seção "O que está acontecendo", cards de trade-off e conclusão textual.
- Doc: `docs/05-tradeoff-report.md` — teoria de filas M/M/c em linguagem simples; como a pontuação é composta; limites do modelo; ADR 0005.
- Branch: `feat/tradeoff-report`

### Fase 6 — Comparação lado a lado (2 dias)
- `GET /runs/compare`; `ComparePage/` reutilizando os componentes da Fase 3 em duas colunas; parágrafo comparativo na conclusão.
- **Entrega:** escolher dois cenários/execuções e comparar.
- Doc: `docs/06-comparison.md` — como alinhar duas séries; critérios de "melhor" (entrega vs latência).
- Branch: `feat/run-comparison`

### Fase 7 — Acabamento e entrega (2 dias)
- Exportar relatório em PDF/PNG para o MinIO via `StorageService` do template (reuso do S3).
- Histórico de execuções por cenário (lista + reabrir).
- Revisão Clean Code: arquivos ≤ 200 linhas Java / componentes pequenos; sem números mágicos; sem código morto.
- README do projeto, `.env.example` atualizado, teste do fluxo `infra/deploy.sh`.
- Merge `develop` → `main` (release v1.0.0).
- Doc: `docs/07-delivery.md` — reuso do storage do template; checklist Clean Code aplicado; retrospectiva do estudo.
- Branches: `feat/report-export`, `docs/project-readme`

### Fase 8 — Brokers reais (6 dias)
O simulador passa a poder rodar contra Kafka, RabbitMQ e SQS de verdade. O engine não muda: cada broker ganha um **adapter real** ao lado do simulado, escolhido por `execution_mode`.

Infra
- `docker-compose.dev.yml`: adicionar `kafka` (KRaft, sem ZooKeeper), `rabbitmq` (com management UI em `:15672`), `localstack` (serviço `sqs,sns`).
- Perfis Spring: `dev-simulated` (padrão) e `dev-live-brokers`.
- Kafka UI ou `kafka-console-consumer` e RabbitMQ Management para inspecionar filas por fora — parte do estudo.

Backend
- Nova coluna `execution_mode ENUM(SIMULATED, REAL)` em `scenarios`; migration `V4__scenario_execution_mode.sql`.
- Interface `MessageBrokerPort` (publish, consume, ack/nack, queueDepth) — porta do domínio; `SimulatedBrokerPort` reaproveita o engine atual.
- Adapters: `KafkaBrokerAdapter` (`spring-kafka`: `KafkaTemplate`, `ConcurrentKafkaListenerContainerFactory` com concorrência = consumidores do cenário, tópico criado com N partições, retry topic + DLT via `@RetryableTopic`), `RabbitMqBrokerAdapter` (`spring-amqp`: fila com `x-max-length` e `x-overflow=drop-head`, DLX, `prefetch`), `SqsBrokerAdapter` (`spring-cloud-aws-sqs` apontando para LocalStack: `VisibilityTimeout`, `RedrivePolicy` com `maxReceiveCount`).
- `LoadGenerator`: publica na taxa do cenário (com burst) usando `ScheduledExecutorService`; injeta falhas no consumidor conforme `failure_pct`.
- `MetricsCollector`: mesma série de ticks do modo simulado, agora medida — backlog via `kafka consumer lag` / `queue.messages_ready` / `ApproximateNumberOfMessages`; latência real por timestamp no header da mensagem; DLQ/DLT contadas do próprio broker.
- Limite de segurança: `MAX_REAL_RATE_PER_SECOND` e `MAX_REAL_DURATION_SECONDS` (constantes) para não derrubar a máquina local.
- Testes: Testcontainers (`kafka`, `rabbitmq`, `localstack`) com um cenário curto por broker validando que ticks e eventos são gerados; testes unitários dos adapters com mocks.

Frontend
- Toggle "Simulado / Real" no `ScenarioForm`; badge no relatório indicando a origem dos dados.
- Painel "Simulado × Real": roda o mesmo cenário nos dois modos e sobrepõe backlog e p95 — a comparação é o resultado central do estudo.
- **Entrega:** rodar o mesmo cenário contra os três brokers reais localmente e comparar com o modelo.
- Doc: `docs/08-real-brokers.md` — subir cada broker; anatomia de tópico/partição, exchange/fila/binding, fila/DLQ do SQS; onde o modelo acertou e errou; ADR 0006 (`ports-and-adapters-for-brokers`).
- Branches: `chore/broker-containers`, `feat/kafka-adapter`, `feat/rabbitmq-adapter`, `feat/sqs-localstack-adapter`, `feat/simulated-vs-real-panel`

**Total estimado: ~29 dias úteis.**

---

## 4. Documentação de estudo (obrigatória em toda fase)

O projeto é objeto de estudo, então cada parte executada deixa uma explicação clara e direta do que foi feito e por quê. Três níveis, todos em inglês no código e em português nos documentos:

### 4.1 `docs/` — um arquivo por fase

```
docs/
  00-foundation.md
  01-scenarios.md
  02-simulation-engine.md
  03-report-dashboard.md
  04-live-simulation.md
  05-tradeoff-report.md
  06-comparison.md
  07-delivery.md
  08-real-brokers.md
  glossary.md          # backlog, lag, DLQ, visibility timeout, partição, M/M/c, p95…
```

Cada `docs/NN-*.md` segue o mesmo esqueleto:

1. **Objetivo** — o que a fase entrega em uma frase.
2. **Conceito de mensageria estudado** — a teoria por trás (ex.: por que Kafka limita paralelismo por partição).
3. **Decisões de design** — alternativas consideradas e a escolhida, com o princípio SOLID que motivou.
4. **Mapa de classes/arquivos** — tabela `arquivo → responsabilidade → depende de`.
5. **Fluxo de uma requisição** — passo a passo de `Controller → Service → Repository` (ou `Page → Hook → Service` no front).
6. **Trechos comentados** — 2 a 4 blocos de código curtos com explicação linha a linha do que importa.
7. **Como testar manualmente** — `curl` ou passos na UI.
8. **O que eu aprendi / erros cometidos** — seção livre, preenchida ao fechar o PR.

### 4.2 Javadoc / TSDoc nas classes públicas

Regra do README: comentário só para o *porquê*. Então cada classe de domínio e service ganha um Javadoc de cabeçalho explicando **papel e motivo**, nunca repetindo o código.

```java
/**
 * Applies RabbitMQ semantics to one simulation tick.
 *
 * Why a separate class: each broker changes how the queue behaves
 * (RabbitMQ drops the oldest messages when max-length is reached,
 * Kafka never drops, SQS delays retries by the visibility timeout).
 * Keeping one class per broker lets us add a new broker without
 * touching {@link SimulationEngine} (Open/Closed).
 */
public final class RabbitMqBehavior implements BrokerBehavior {

    /** RabbitMQ overflow policy `drop-head`: the oldest messages are discarded first. */
    @Override
    public OverflowResult applyOverflow(SimulationState state, Scenario scenario) { … }
}
```

```ts
/**
 * Opens an SSE connection for a live run and feeds ticks into React state.
 *
 * Why SSE and not WebSocket: the server only pushes; the client never sends
 * mid-run messages (stop is a plain POST). SSE reconnects natively.
 */
export function useLiveRun(runId: string | null) { … }
```

O que **não** se documenta: getters, DTOs triviais, mappers gerados, código autoexplicativo.

### 4.3 ADRs — decisões de arquitetura

Decisões que afetam mais de uma fase vão para `docs/adr/NNNN-titulo.md` (formato curto: Contexto · Decisão · Consequências). Já previstos:

- `0001-java-spring-postgresql.md`
- `0002-sse-for-live-simulation.md`
- `0003-broker-behavior-strategy.md`
- `0004-deterministic-seed.md`
- `0005-analytical-model-mmc.md`
- `0006-ports-and-adapters-for-brokers.md`

### 4.4 Testes como documentação

Nomes de teste descrevem o comportamento estudado, em inglês, no padrão `should_<resultado>_when_<condição>`:

```java
@Test void should_capConsumersAtPartitionCount_when_kafkaHasMoreConsumersThanPartitions()
@Test void should_dropOldestMessages_when_rabbitQueueExceedsMaxLength()
@Test void should_delayRetryByVisibilityTimeout_when_sqsMessageFails()
@Test void should_growBacklogLinearly_when_loadExceedsCapacity()
```

Cada teste tem um comentário de uma linha com o *porquê* do cenário (ex.: `// Kafka assigns each partition to exactly one consumer in a group`).

### 4.5 Onde isso entra no ritual

- O PR de cada fase inclui o `docs/NN-*.md` e os ADRs novos; o revisor confere a doc junto com o código.
- Trechos comentados da doc são **copiados do código final**, não escritos antes (evita doc divergente).
- `README.md` do projeto ganha uma seção **“Roteiro de estudo”** com links para os oito docs na ordem.

---

## 5. Como cada fase é elaborada (ritual por PR)

1. Criar branch `<type>/<descricao>` a partir de `develop`.
2. Escrever primeiro o **contrato**: DTOs + assinatura do service + teste que falha.
3. Implementar service → repository → controller (Java) ou service → hook → page (React).
4. Rodar checklist: `./mvnw verify` · `npm run lint && npm run build`.
5. Escrever `docs/NN-*.md` (seções 1–7) e ADRs a partir do código final.
6. Commit em Conventional Commits (`feat(simulation): add kafka partition behavior`).
7. PR para `develop`, título no mesmo formato, uma revisão, squash merge.
8. Preencher a seção 8 da doc (“O que eu aprendi”) antes do merge.

---

## 6. Estrutura final

```
msgsim/
  backend/src/main/java/com/bimd/msgsim/
    config/  controller/  security/  exception/  util/
    domain/model/  domain/dto/  domain/mapper/
    repository/
    service/
      scenario/      ScenarioService, ScenarioValidator
      simulation/    SimulationEngine, SimulationState, LiveSimulationService, LoadGenerator, MetricsCollector,
                     broker/ (BrokerBehavior, KafkaBehavior, RabbitMqBehavior, SqsBehavior),
                     port/   (MessageBrokerPort, SimulatedBrokerPort, KafkaBrokerAdapter, RabbitMqBrokerAdapter, SqsBrokerAdapter),
                     event/  (EventDetector + implementações)
      report/        AnalyticalQueueModel, BrokerTradeoffService, CostEstimator,
                     pricing/ (BrokerPricing + implementações), ReportNarrativeService
  backend/src/main/resources/db/migration/
  frontend/src/
    api/modules/     api.ts, types.ts, auth.service.ts, scenario.service.ts, run.service.ts, report.service.ts
    components/ui/   StatCard, HelpLabel, OutcomeBar
    components/layout/
    hooks/           useAuth, useTheme, useLiveRun
    pages/           ScenariosPage/, ReportPage/, ComparePage/, LoginPage/
    theme.ts  router.tsx
  docs/            00-foundation.md … 08-real-brokers.md, glossary.md, adr/
  docker-compose.dev.yml  ARCHITECTURE.md  README.md
```

---

## 7. Riscos e mitigação

- **Volume de ticks** (3600 s × muitas runs): índice em `(run_id, second)`; opção de downsample na leitura (`?step=5`) para gráficos.
- **SSE atrás do Nginx**: desabilitar buffering (`proxy_buffering off`) na rota `/api/runs/*/stream`.
- **Fidelidade do modelo**: manter o motor estatístico simplificado e explicitar na UI que custos são ilustrativos; seed fixo garante reprodutibilidade nos testes.
- **Escopo**: autenticação, perfil e storage vêm prontos do template — não reimplementar.
- **Brokers reais na máquina local**: Kafka + RabbitMQ + LocalStack consomem ~3 GB de RAM; subir só o broker do cenário em teste (`docker compose up kafka`), limites `MAX_REAL_*` no backend.
- **Divergência simulado × real**: esperada e desejada — é o objeto de estudo; documentar em `docs/08` em vez de "corrigir" o modelo para bater.
- **Doc desatualizada**: trechos da doc são copiados do código final e o PR só fecha com a doc da fase revisada.
