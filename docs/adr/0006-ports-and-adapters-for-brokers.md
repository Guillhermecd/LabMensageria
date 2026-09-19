# ADR 0006 — Ports and adapters para execução real (`MessageBrokerPort`)

## Contexto

O `PLAN.md` previa a Fase 8 rodando cenários de verdade em Kafka, RabbitMQ e SQS reais (Testcontainers, 6 dias). Escopo reduzido para um único broker real — RabbitMQ, já que é o mais simples de rodar localmente via Docker sem exigir Zookeeper/KRaft (Kafka) ou uma conta AWS (SQS). A pergunta de design não muda com o corte de escopo: como conectar "execução real" ao resto do sistema sem transformar isso num hack específico do RabbitMQ, e sem que o caminho real toque o motor simulado.

Duas restrições vieram do `PLAN.md`: (1) a divergência entre simulado e real é o próprio objeto de estudo, então `SimulationEngine.step()` não pode ganhar um `if (real)` — ele continua descrevendo só o modelo matemático; (2) adicionar Kafka/SQS depois não pode significar reabrir esta decisão.

## Decisão

Mesma forma do `BrokerBehavior` (ADR 0003), mas para o mundo real: interface `MessageBrokerPort` com `setUp`/`publish`/`registerConsumer`/`stopConsumer`/`queueDepth`/`tearDown`/`isHealthy`. `RabbitMqBrokerAdapter` é a única implementação hoje. `RealSimulationService` é o orquestrador — publica em ritmo real via `ScheduledExecutorService`, decide pass/fail/retry/DLQ por mensagem (mesma lógica de `failurePct`/`maxRetries`/`dlqEnabled` do motor simulado, mas aplicada a mensagens de verdade), e monta `TickResult` a partir de números medidos (profundidade real da fila, latência real por mensagem) — nunca chama `SimulationEngine.step()`.

`Scenario.executionMode` (`SIMULATED` | `REAL`) decide, em `RunController`, qual serviço atende `/stream` e `/stop`. `ScenarioValidator` restringe `executionMode=REAL` a `broker=RABBITMQ` e aplica tetos (`ratePerSecond <= 500`, `durationSeconds <= 120`) — uma carga real descontrolada pode sobrecarregar a máquina de um jeito que um tick simulado nunca sobrecarrega.

## Consequências

- Adicionar Kafka/SQS reais depois é uma classe nova implementando `MessageBrokerPort`, mais uma entrada no validador — sem tocar `RealSimulationService` nem o motor simulado.
- `SimulationPersistence.complete(...)` ganhou uma sobrecarga que recebe totais (`long`) em vez de `SimulationState`, para ser reaproveitada pelo caminho real sem forçar `RealSimulationService` a construir um `SimulationState` fake só para reusar o método.
- Fila é efêmera por execução (`msgsim-<runId>`), sempre limpa em `finally` (purge + delete) — mesmo numa parada manual ou erro, para a próxima execução do mesmo cenário não herdar backlog sujo.
- Custo aceito: `RabbitMqBrokerAdapter` usa um header próprio (`x-msgsim-retry-count`) para contar tentativas, em vez do `x-death` nativo do RabbitMQ — mais simples de ler, mas não é o mecanismo de DLQ nativo do broker (`x-dead-letter-exchange`). Documentado aqui para não ser confundido com uma DLQ "de verdade" do RabbitMQ.
