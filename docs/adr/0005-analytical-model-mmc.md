# ADR 0005 — Modelo analítico M/M/c simplificado

## Contexto

A Fase 5 precisa responder "qual broker seria melhor para esta carga?" sem depender de rodar uma simulação — o cenário pode nem ter sido executado ainda. A resposta certa exige teoria de filas de verdade (M/M/c: chegadas Poisson, `c` servidores, tempo de serviço exponencial), mas uma implementação completa (fórmula C de Erlang, distribuições de espera) é desproporcional ao escopo de um comparador ilustrativo entre três brokers.

## Decisão

Usar uma aproximação fechada e simples: `capacidade = consumidores_efetivos × 1000 / processing_ms`, `ratio = carga / capacidade`, e um termo de espera que cresce com `ratio^c` quando estável e linearmente quando instável (`ratio ≥ 1`). Não é M/M/c exato — é rápido de calcular, fácil de explicar (`docs/05-tradeoff-report.md` traduz cada termo) e captura a propriedade que mais importa para o estudo: abaixo de 1× a fila se estabiliza, acima disso ela cresce sem limite.

## Consequências

- O modelo erra sistematicamente para trás (é conservador): a fórmula de espera simplificada tende a superestimar a latência sob carga alta, então o `ReportNarrativeService.conclusion` sinaliza explicitamente quando o p50 medido diverge muito do estimado ("o modelo analítico é conservador") em vez de fingir que os dois batem.
- Reaproveita `BrokerBehavior` (mesma interface do motor de simulação) para consumidores efetivos e overhead de latência — os números analíticos e os números simulados nunca divergem por engano de digitação de uma constante em dois lugares.
- Não modela filas com prioridade, múltiplas classes de mensagem, ou correlação entre chegadas (Poisson assume independência) — o estudo é sobre comparar Kafka/RabbitMQ/SQS sob a mesma carga simplificada, não sobre precisão de teoria de filas.
