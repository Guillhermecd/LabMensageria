# ADR 0003 — Broker behavior via Strategy (`BrokerBehavior`)

## Contexto

O motor de simulação precisa aplicar regras diferentes por broker a cada tick: capacidade efetiva (Kafka limita por partição), overflow de fila (só RabbitMQ descarta), atraso de retry (só SQS espera visibility timeout) e overhead de latência. O `PLAN.md` já previa três brokers na Fase 2 e mais adaptadores reais na Fase 8 — o número de "casos especiais por broker" só cresce.

## Decisão

Extrair uma interface `BrokerBehavior` com um método por regra (`effectiveConsumers`, `applyOverflow`, `retryDelaySeconds`, `latencyOverheadMs`, `firstDlqMessage`). Cada broker é uma classe `@Component` que implementa a interface. `SimulationEngine` recebe `List<BrokerBehavior>` no construtor e indexa por `BrokerType` — nunca contém um `if/switch` sobre broker.

## Consequências

- Adicionar um broker (ou, na Fase 8, um adapter real) é uma classe nova; `SimulationEngine` não muda (Open/Closed).
- Cada regra de broker é testável isoladamente (`KafkaBehavior.effectiveConsumers`, sem precisar rodar uma simulação inteira).
- Custo: uma interface com 5 métodos é mais cerimônia do que um único método `step(sc)` por broker, como no protótipo original. Aceito porque o motor genérico (produção, filas de retry, detecção de eventos) é idêntico nos três — só as 5 regras variam.
