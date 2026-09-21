# 02 — Motor de simulação (instantâneo)

## 1. Objetivo

Rodar um cenário do início ao fim em memória e persistir a série de ticks e os eventos detectados, disponíveis via API.

## 2. Conceito de mensageria estudado

**Capacidade de um consumidor** não é um número fixo — depende de quantos consumidores estão realmente ativos e de quanto tempo cada mensagem leva para processar:

```
capacidade ≈ consumidores_efetivos × 1000 ÷ processing_ms
```

`consumidores_efetivos` é o ponto onde os três brokers divergem:

- **Kafka**: uma partição só é lida por um consumidor do grupo. Com 10 consumidores e 6 partições, só 6 processam — os outros 4 ficam ociosos. `consumidores_efetivos = min(consumers, partitions)`.
- **RabbitMQ / SQS**: qualquer consumidor pode ler de qualquer lugar da fila. `consumidores_efetivos = consumers`.

Quando a produção (`rate`) ultrapassa a capacidade, o backlog cresce a cada tick — não existe um "limite" absorvendo o excesso, exceto no RabbitMQ com `queue_capacity` configurado (aí o excedente é descartado, não acumulado).

## 3. Decisões de design

- **`BrokerBehavior` como ponto de extensão único.** `SimulationEngine.step` nunca teve um `if (broker == KAFKA)`. Toda decisão específica de broker — consumidores efetivos, overflow de fila, atraso de retry, overhead de latência, texto da primeira DLQ — está atrás da interface. Adicionar um broker novo (Fase 8: brokers reais) não toca o engine.
- **`SimulationState` mutável, mas confinado ao pacote `service.simulation`.** O estado de uma execução (fila, contadores, buckets de retry) muda a cada tick; não faz sentido modelar isso como imutável. Só os métodos necessários para os `BrokerBehavior`s (fila, contagem de dropped, eventos) são `public`; o resto é acesso de pacote, só o `SimulationEngine` mexe.
- **Seed determinístico via `java.util.Random(seed)`**, guardado em `simulation_runs.seed`. A mesma seed reproduz exatamente a mesma série de ticks — essencial para comparar "antes/depois" de uma mudança de parâmetro sem ruído da aleatoriedade.
- **Persistência em lote (`saveAll`), não tick a tick.** O motor roda inteiro em memória (`runToCompletion`) e só then grava; evita 120+ round-trips ao banco por execução instantânea.
- **Detecção de eventos como métodos privados no engine, não classes por tipo (ainda).** O `PLAN.md` previa uma classe por tipo de evento; na prática as quatro detecções (saturação, lag, burst, DLQ) são muito curtas e compartilham o mesmo `SimulationState` mutável no meio do `step()` — extrair para classes obrigaria passar 6+ parâmetros ou reabrir o estado como público. Decisão registrada aqui para revisitar se o número de tipos de evento crescer.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `service/simulation/SimulationEngine` | Avança o estado um segundo por vez; detecta eventos | `BrokerBehavior` (injetado como lista) |
| `service/simulation/SimulationState` | Estado mutável de uma execução (fila, contadores, ticks, eventos) | — |
| `service/simulation/broker/BrokerBehavior` | Contrato de regras específicas de broker | `Scenario`, `SimulationState` |
| `service/simulation/broker/KafkaBehavior` | Partições limitam paralelismo; nunca descarta | — |
| `service/simulation/broker/RabbitMqBehavior` | Fila com `max-length`; descarta ao encher | — |
| `service/simulation/broker/SqsBehavior` | Retry só após visibility timeout | — |
| `service/simulation/SimulationRunService` | Orquestra: cria run, roda o engine, grava em lote, calcula totais | `SimulationEngine`, repositórios |
| `controller/RunController` | Rotas `/api/scenarios/{id}/runs`, `/api/runs/{id}*` | `SimulationRunService` |

## 5. Fluxo de uma requisição

`POST /api/scenarios/{id}/runs?mode=INSTANT`:

1. `RunController` rejeita `mode=LIVE` (Fase 4) com 400.
2. `SimulationRunService.runInstant` confirma que o cenário pertence ao usuário autenticado, cria a linha `simulation_runs` com `status=RUNNING`.
3. `SimulationEngine.runToCompletion` roda `step()` em loop até `state.isDone()` — tudo em memória, sem tocar o banco.
4. Os ticks e eventos acumulados em `SimulationState` são convertidos para entidades e gravados com `saveAll` (dois lotes, não um insert por tick).
5. O run é atualizado para `status=COMPLETED`, com os totais (`producedTotal`, `deliveredTotal`, `dlqTotal`, `droppedTotal`, `retriesTotal`) extraídos direto do estado final.

## 6. Trechos comentados

```java
// SimulationEngine.java — por que o overflow do RabbitMQ vive no comportamento,
// não no engine: só o RabbitMQ descarta mensagens por fila cheia. Colocar essa
// regra aqui obrigaria o engine a saber qual broker está rodando.
int droppedThisTick = behavior.applyOverflow(sim, scenario, t);
```

```java
// RabbitMqBehavior.java — por que dropSeen é uma flag, não um contador:
// o evento QUEUE_FULL é informativo ("a fila começou a descartar"), não
// precisa disparar de novo a cada tick em que o overflow continua.
if (!state.isDropSeen()) {
    state.setDropSeen(true);
    state.addEvent(new EventResult(second, EventType.QUEUE_FULL, ...));
}
```

```java
// SqsBehavior.java — por que o retry não volta no mesmo tick:
// no SQS real, uma mensagem que falhou fica invisível até o visibility
// timeout expirar. O engine modela isso agendando o retorno à fila para
// t + visibilityDelay em vez de devolver a mensagem imediatamente.
if (retried > 0) {
    if (visibilityDelay > 1) {
        sim.retryBuckets.add(new RetryBucket(t + visibilityDelay, retried));
    } else {
        sim.setQueue(sim.getQueue() + retried);
    }
}
```

## 7. Como testar manualmente

```sh
TOKEN=$(curl -s -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}' | jq -r .token)

SCENARIO_ID=$(curl -s -X POST http://localhost:1337/api/scenarios \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Kafka run test","broker":"KAFKA","ratePerSecond":200,"consumers":4,"processingMs":15,"failurePct":1,"maxRetries":3,"messageSizeKb":2,"durationSeconds":30,"partitions":6,"dlqEnabled":true,"burstEnabled":false}' | jq -r .id)

RUN_ID=$(curl -s -X POST "http://localhost:1337/api/scenarios/$SCENARIO_ID/runs?mode=INSTANT&seed=42" \
  -H "Authorization: Bearer $TOKEN" | jq -r .id)

curl -s http://localhost:1337/api/runs/$RUN_ID -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:1337/api/runs/$RUN_ID/ticks -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:1337/api/runs/$RUN_ID/events -H "Authorization: Bearer $TOKEN"
```

## 8. O que eu aprendi / erros cometidos

- Testado ao vivo com seed fixa (42): 30 ticks, `producedTotal=5995`, `deliveredTotal=5993`, `retriesTotal=63`, evento `FINISHED` com backlog residual — bateu com o esperado pra um cenário Kafka saudável (folga de capacidade, poucas falhas).
- Erro de schema: mapeei `double` em Java esperando `NUMERIC(10,2)` no Postgres, mas o Hibernate valida `double` como `float8`/`double precision`, não `numeric`. `ddl-auto=validate` pegou a divergência no boot (`Schema-validation: wrong column type`) — corrigido trocando `NUMERIC` por `DOUBLE PRECISION` na migration. Lição: ao mapear `double`/`Double` em Java, a coluna Postgres correspondente é `double precision`, não `numeric`.
- `SimulationState` package-private com getters/setters pontuais para os campos que `BrokerBehavior` precisa tocar (`queue`, `dropped`, `dropSeen`, eventos) é mais simples do que abrir a classe inteira como pública — só RabbitMQ precisa mutar a fila de fora do engine.

## 7. Motor por eventos discretos (Etapa 1 do plano de confiabilidade)

O avanço por tick de 1 s foi substituído por uma fila de eventos futuros (min-heap) ordenada por instante em ms. O relógio salta para o próximo evento; `step()` continua avançando até a próxima fronteira de 1 s e emitindo um `TickResult` (é a unidade do stream ao vivo e da série persistida).

- **Eventos:** `ARRIVAL` (chegada Poisson, taxa por segundo seguindo burst/pico), `COMPLETION` (fim de serviço; sorteia falha), `REAPPEAR` (retry com atraso, ex.: visibility timeout do SQS) e `SWEEP` (varredura a cada segundo: overflow do broker, fechamento do tick, capacidade do próximo segundo). Empates no mesmo instante: conclusão, reaparecimento, chegada, varredura.
- **Cada mensagem é rastreada**, então p50/p95/p99 são percentis de latência reais (espera + serviço + overhead do broker), não fórmulas. `RunStatistics` usa o conjunto de mensagens entregues após o warmup.
- **Três streams de RNG** (chegadas, tempo de serviço, falhas) derivados de uma seed via `SplittableRandom.split()`. Mudar a taxa de falha não altera as chegadas; dois brokers com a mesma seed recebem as mesmas mensagens nos mesmos instantes.
- **Variação do serviço** de verdade: `CONSTANT` (tempo fixo), `EXPONENTIAL` (média `processingMs`) e `HEAVY_TAIL` (5% das mensagens a 10× a média; as demais encurtadas para manter a média).
- **Tentativas:** uma mensagem que falha é reenfileirada até `maxRetries` novas tentativas; ao esgotar, vai para a DLQ (se `dlqEnabled`; sem DLQ continua tentando).
- **Invariantes em toda simulação:** conservação `produzidas = entregues + pendentes + descartadas + dlq` (lança `IllegalStateException` se falhar) e lei de Little (`L = λ·W`, com L pela integral exata no tempo; aviso em `SimulationState.getWarnings()` e no log se o erro passar de 2%).
- **Calibração:** `SimulationEngineTest.should_matchErlangC_when_arrivalsAndServiceAreExponential` compara a espera média com a fórmula de Erlang-C (M/M/c) em 30/50/70/85% de ocupação, tolerância de 5%.
- **Limite conhecido:** só mensagens entregues entram nas latências; em saturação o backlog ainda não servido não aparece nelas (tratado na Etapa 4, modo saturado).

## 8. Comportamento por broker (Etapa 2)

`BrokerBehavior` agora tem quatro pontos de decisão; o resto do motor é genérico. Parâmetros nulos no cenário caem nos padrões abaixo, então o mesmo cenário roda contra os três brokers.

| | admit | parallelism | retryDelayMs | sweep |
|---|---|---|---|---|
| Kafka | sempre aceita (append) | `min(consumidores, partições)` (padrão 6) | 0 (offset não avança) | apaga os mais antigos se `fila × tamanho` > retenção (256 MB) ou idade > 168 h |
| RabbitMQ | `BLOCK_PRODUCER` acima do high watermark (256 MB); nada é descartado | consumidores ajustados pelo prefetch (250) | 0 (nack) | vazio, salvo `queueCapacity` (max-length drop-head) explícito |
| SQS | sempre aceita | `min(consumidores, in-flight máx − ocultas)` (120000) | visibility timeout (30 s) | retenção de 4 dias |

- `BLOCK_PRODUCER` não perde mensagem: o motor deixa de agendar chegadas e retoma quando o broker voltaria a aceitar. O tempo bloqueado fica em `SimulationState.getBlockedSeconds()`.
- Retenção e high watermark valem 256 MB por padrão de propósito: com os valores de produção (dias de log, GB de memória) uma simulação de 120 s nunca os atingiria. O tamanho da mensagem entra na conta (`MB × 1024 ÷ KB`).
- Prefetch: eficiência = `serviço ÷ (serviço + 2 ms ÷ prefetch)`; com prefetch alto é ~1, com prefetch 1 o consumidor espera uma entrega por mensagem. É uma aproximação, não o protocolo.
- Tentativas: ao esgotar `maxRetries`, a mensagem vai para a DLQ; sem DLQ é descartada (contada como descartada).
