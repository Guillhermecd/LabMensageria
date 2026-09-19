# 05 — Relatório analítico e trade-offs

## 1. Objetivo

Comparar Kafka, RabbitMQ e SQS para o mesmo cenário sem precisar rodar uma simulação (`/scenarios/{id}/tradeoffs`), e gerar uma narrativa + conclusão em texto a partir de uma execução real (`/runs/{id}/report`).

## 2. Conceito de mensageria estudado

**Teoria de filas M/M/c, em português simples**: um sistema de fila tem "M/M/c" quando mensagens chegam de forma aleatória e independente (Poisson), o tempo de processamento também é aleatório (exponencial), e há `c` consumidores trabalhando em paralelo. A pergunta central é: com essa carga e esses `c` consumidores, o sistema se estabiliza ou a fila cresce para sempre?

A resposta depende só de uma razão: `carga ÷ capacidade`. Abaixo de 1×, o sistema absorve variações de tráfego — mensagens esperam um pouco, mas a fila não cresce indefinidamente. Acima de 1×, não importa quanto se espere: a fila cresce sem limite, porque chega mais trabalho do que sai.

Este projeto usa uma versão **simplificada** dessa teoria (ver ADR 0005) — o suficiente para comparar três brokers sob a mesma carga, não uma calculadora de capacidade para produção.

## 3. Decisões de design

- **Pontuação com pesos nomeados, não mágicos.** `BrokerTradeoffService` usa `STABILITY_WEIGHT = 0.35`, `LATENCY_WEIGHT = 0.25`, `LOSS_WEIGHT = 0.20`, `COST_WEIGHT = 0.10`, `OPS_WEIGHT = 0.10`. A pontuação de 0 a 100 que decide "qual broker é o melhor" é o número mais visível do relatório — qualquer um lendo o código six meses depois precisa ver de onde ele vem sem arqueologia.
- **`BrokerBehavior` estendido, não duplicado.** `AnalyticalQueueModel` reaproveita `effectiveConsumers()` e `latencyOverheadMs()` da mesma interface que o motor de simulação usa (Fase 2). Dois métodos novos (`analyticalRetryDelayMs`, `operationalSimplicityScore`) foram adicionados à interface em vez de criar um mapa paralelo de constantes por broker — mesmo padrão Strategy, mesmo ponto de extensão.
- **Custo estimado por uma interface própria (`BrokerPricing`), não dentro do modelo.** Kafka cobra por hora de broker, SQS por requisição, RabbitMQ por hora de nó — três formas de cobrança genuinamente diferentes. Uma implementação por broker evita um `if/else` de precificação dentro do `AnalyticalQueueModel`.
- **Locale fixo (`pt-BR`) nos textos gerados**, não o locale padrão da JVM. O texto do relatório é sempre em português ("1,13× de ocupação", "$0,005"); usar `String.format` sem locale explícito faz o separador decimal (vírgula vs ponto) depender de como o servidor está configurado — quebraria em produção sem avisar. Ver seção 8.
- **`/scenarios/{id}/tradeoffs` não depende de uma run existir.** O modelo analítico responde com os parâmetros do cenário sozinho; o card de cada broker aparece assim que o cenário é selecionado, antes mesmo de clicar em "Resultado instantâneo".

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `service/report/AnalyticalQueueModel` | Estimativa fechada (capacidade, latência, backlog, perda) por broker | `BrokerBehavior`, `CostEstimator` |
| `service/report/BrokerTradeoffService` | Pontua os 3 brokers e decide o melhor | `AnalyticalQueueModel` |
| `service/report/CostEstimator` + `pricing/*` | Custo ilustrativo, uma implementação por broker | — |
| `service/report/ReportNarrativeService` | Narrativa, insights e conclusão em texto, a partir de uma run real | `BrokerBehavior`, `TradeoffResult` |
| `service/report/ReportQueryService` | Fachada: checagem de dono + DTOs para os dois endpoints | `ScenarioRepository`, `SimulationRunService` |
| `pages/ReportPage/TradeoffCards.tsx` | Um card por broker, o melhor destacado | `Tradeoff[]` |
| `pages/ReportPage/InsightsList.tsx`, `NarrativePanel.tsx`, `ConclusionPanel.tsx` | Seção "O que está acontecendo", narrativa e conclusão | `AnalyticalReport` |

## 5. Fluxo de uma requisição

`GET /api/scenarios/{id}/tradeoffs`:

1. `ReportQueryService.tradeoffs` confirma o dono do cenário.
2. `BrokerTradeoffService.evaluate` chama `AnalyticalQueueModel.compute` uma vez por broker (`BrokerType.values()`), normaliza latência e custo entre os três, soma os pesos e escolhe o melhor.
3. Cada linha vira um `TradeoffResponse` com prós/contras específicos do broker (texto fixo, mais um contra dinâmico "carga X× a capacidade" quando instável).

`GET /api/runs/{runId}/report`:

1. `ReportQueryService.report` busca a run (dono confirmado via `SimulationRunService.findOwnedRun`) e seus ticks.
2. Roda `BrokerTradeoffService.evaluate` no cenário da run (mesma lógica do endpoint acima).
3. `ReportNarrativeService` gera narrativa (texto corrido dos ticks), insights (lista curta de observações) e conclusão (compara o broker configurado com o melhor do modelo, citando `mainDelta` — a maior diferença entre os dois).

## 6. Trechos comentados

```java
// AnalyticalQueueModel.java — por que reusa BrokerBehavior em vez de
// duplicar "kafka:5, rabbit:2, sqs:20" aqui: se o overhead de latência
// do motor de simulação mudar, o modelo analítico muda junto — os dois
// nunca ficam contando histórias diferentes sobre o mesmo broker.
int overhead = behavior.latencyOverheadMs();
```

```java
// ReportNarrativeService.java — por que o format() local força pt-BR:
// o texto ao redor do número já está em português ("de ocupação",
// "mensagens entregues"); um número formatado com ponto decimal
// ("1.13x") no meio de uma frase em português é um bug de i18n, não
// um detalhe cosmético. String.format sem Locale usa o locale padrão
// da JVM, que muda de máquina para máquina — fixar o locale aqui é o
// que garante "1,13×" sempre, em qualquer servidor.
private static String format(String pattern, Object... args) {
    return String.format(Locale.forLanguageTag("pt-BR"), pattern, args);
}
```

## 7. Como testar manualmente

```sh
TOKEN=$(curl -s -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}' | jq -r .token)

# não precisa de run nenhuma rodando:
curl -s "http://localhost:1337/api/scenarios/$SCENARIO_ID/tradeoffs" -H "Authorization: Bearer $TOKEN"

RUN_ID=$(curl -s -X POST "http://localhost:1337/api/scenarios/$SCENARIO_ID/runs?mode=INSTANT" \
  -H "Authorization: Bearer $TOKEN" | jq -r .id)
curl -s "http://localhost:1337/api/runs/$RUN_ID/report" -H "Authorization: Bearer $TOKEN"
```

Na UI: selecionar qualquer cenário e confirmar que os três cards de broker aparecem imediatamente (sem rodar nada); rodar "Resultado instantâneo" e confirmar que "O que está acontecendo", "Narrativa" e "Conclusão" aparecem citando o broker vencedor do modelo.

## 8. O que eu aprendi / erros cometidos

- **Bug real pego na primeira chamada de API, não em teste unitário**: o número no JSON de resposta veio como `"1,13"` (vírgula) em vez de `"1.13"` — porque `String.format("%.2f", ...)` usa o locale padrão da JVM, e esta máquina está configurada como `pt_BR` (visto no output do `mvn -v` lá na Fase 0). Funcionaria por acidente aqui e quebraria em qualquer ambiente com locale diferente (a maioria dos containers Docker usa `C`/`en-US`). Corrigido fixando `Locale.forLanguageTag("pt-BR")` explicitamente em um helper `format()` — já que o texto ao redor é sempre português, a vírgula decimal está certa, só não podia depender do acaso do locale do servidor.
- Testado que a pontuação muda de vencedor quando o Kafka fica artificialmente limitado por partições (`consumers=20, partitions=2`) — o teste `should_changeBestBroker_when_consumersExceedPartitions` cristaliza esse comportamento. Uma primeira versão do teste (`should_pickKafka_when_partitionsMatchConsumers`) partia de uma suposição errada — que Kafka ganharia sempre que não tivesse consumidores ociosos — e falhou porque RabbitMQ e SQS têm `operationalSimplicityScore` mais alto; removi esse teste em vez de forçar a asserção a bater com um resultado que não era garantido pelo algoritmo.
- Validado no navegador: os cards de trade-off aparecem antes de qualquer simulação rodar (contrato "não depende de run"), e a conclusão em texto realmente cita o broker vencedor pelo nome com a pontuação — bateu com a saída da API testada via curl.
