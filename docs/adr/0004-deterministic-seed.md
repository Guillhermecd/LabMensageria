# ADR 0004 — Seed determinístico por execução

## Contexto

O motor usa números aleatórios em três fontes (intervalos de chegada, tempo de serviço e falhas) para simular variação real de tráfego. Aleatoriedade não seedada torna impossível reproduzir uma execução específica — necessário tanto para testes automatizados (`should_growBacklogLinearly_when_loadExceedsCapacity` precisa de um resultado estável) quanto para o estudo em si (comparar "antes/depois" de mudar um parâmetro sem o ruído da aleatoriedade escondendo o efeito real).

## Decisão

Cada `SimulationState` deriva de uma única seed três streams independentes (`SplittableRandom.split()`: chegadas, serviço, falhas), de modo que mudar a taxa de falha não altera a sequência de chegadas. A seed é gerada automaticamente (`System.nanoTime()`) quando o usuário não informa uma, mas sempre persistida em `simulation_runs.seed` e aceita como parâmetro opcional na API (`POST /scenarios/{id}/runs?seed=...`). Rodar a mesma seed sobre o mesmo cenário produz exatamente a mesma série de ticks.

## Consequências

- Testes de motor usam uma seed fixa e podem afirmar propriedades do resultado (backlog cresce, dropped > 0) sem flakiness.
- Reexecutar uma simulação para depurar um comportamento estranho é possível: basta usar a seed registrada no `simulation_runs`.
- Custo: a série de ticks para uma seed fixa é determinística *apenas* para uma dada versão do `SimulationEngine` — mudar a ordem ou o número de sorteios em cada stream muda os resultados de todas as seeds já gravadas. Não há garantia de compatibilidade entre versões do motor.
