# 06 — Comparação lado a lado

## 1. Objetivo

Escolher dois cenários, rodar os dois e ver os resultados lado a lado — mesmos KPIs, mesmos gráficos, mais um parágrafo dizendo qual dos dois se saiu melhor e por quê.

## 2. Conceito de mensageria estudado

**Alinhar duas séries** aqui não significa reamostrar ou interpolar timestamps — as duas simulações já rodam na mesma unidade de tempo (segundos desde `t=0`) e cada tick já tem `second` como chave natural. "Alinhado" quer dizer só isso: os dois eixos X das duas colunas representam a mesma coisa (segundo da simulação), então colocá-los lado a lado já é comparável sem nenhum processamento extra. Se as durações forem diferentes, cada gráfico simplesmente vai até onde tem dado — não há truncamento nem preenchimento artificial.

**Critério de "melhor"**: não existe um número único que resuma "melhor". O comparador usa dois critérios explícitos — **taxa de entrega** (`delivered / produced`) e **latência de cauda** (`p95` do último tick) — porque são os dois que mais frequentemente entram em conflito na prática (um broker pode entregar mais devagar, outro pode ser mais rápido mas perder mais mensagens). Um cenário só é declarado "melhor" quando vence nos dois ao mesmo tempo; caso contrário, o texto deixa explícito que a escolha depende do que importa mais para quem está lendo.

## 3. Decisões de design

- **Mesmos componentes da Fase 3, sem versão "de comparação".** `ComparisonColumn` é uma composição fina de `KpiGrid` + `ThroughputChart` + `BacklogChart` + `LatencyChart` + `ConsumerUtilization` + `MessageOutcomeBar` — todos importados diretamente de `pages/ReportPage/`. Nenhum desses componentes sabe que está sendo usado em modo de comparação; eles só recebem `scenario`/`run`/`ticks` como sempre receberam.
- **Endpoint único (`GET /runs/compare?a=&b=`) devolve tudo pronto.** Em vez do frontend fazer 6 chamadas (scenario A, run A, ticks A, eventos A, scenario B, ...), `CompareService` monta os dois `RunReportResponse` no backend e ainda calcula o parágrafo comparativo — o frontend faz uma chamada, recebe os dois lados prontos para os componentes existentes.
- **Comparar cenários dispara duas runs novas, não reaproveita execuções antigas.** `useCompare.compare()` chama `RunService.runInstant` duas vezes (uma por cenário) antes de pedir a comparação — garante que os dois lados foram medidos sob as mesmas condições momentâneas (sem um lado com uma run de ontem e outro de agora).
- **`CompareService` é um serviço à parte, não um método a mais em `ReportQueryService`.** Ele tem uma única responsabilidade (montar os dois lados + o texto comparativo) e usa um conjunto de dependências ligeiramente diferente (mapeia tick/evento diretamente via `SimulationRunMapper`, sem passar pelo `BrokerTradeoffService`). Colocar tudo em `ReportQueryService` deixaria a classe respondendo por três contratos de API sem relação direta entre si.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `service/report/CompareService` | Monta os dois `RunReportResponse` e o parágrafo comparativo | `SimulationRunService`, `ScenarioMapper`, `SimulationRunMapper` |
| `domain/dto/RunReportResponse` | Pacote (cenário + run + ticks + eventos) reaproveitado dos dois lados | — |
| `domain/dto/CompareResponse` | `{ a, b, comparison }` | `RunReportResponse` |
| `pages/ComparePage/useCompare.ts` | Lista cenários, dispara as duas runs, busca a comparação | `ScenarioService`, `RunService` |
| `pages/ComparePage/ComparisonColumn.tsx` | Uma coluna = componentes da Fase 3 recebendo um `RunReport` | Componentes de `ReportPage/` |
| `pages/ComparePage/ComparePage.tsx` | Dois seletores de cenário + botão + duas colunas + parágrafo | `useCompare` |

## 5. Fluxo de uma requisição

`GET /api/runs/compare?a={runIdA}&b={runIdB}`:

1. `CompareService.compare` chama `loadReport` duas vezes — cada uma confirma o dono via `SimulationRunService.findOwnedRun` e busca ticks/eventos.
2. `compareText` calcula taxa de entrega e p95 final dos dois lados e decide o veredito (A vence nos dois, B vence nos dois, ou "cada um vence em um critério").
3. Retorna os dois `RunReportResponse` completos + o texto — o frontend não precisa fazer nenhuma chamada adicional para renderizar as duas colunas.

Frontend (`useCompare.compare`):

1. `Promise.all([RunService.runInstant(scenarioIdA), RunService.runInstant(scenarioIdB)])` — as duas rodam em paralelo.
2. Com os dois `runId` em mãos, uma única chamada a `RunService.compare(runA.id, runB.id)`.
3. `ComparePage` renderiza o parágrafo e duas `ComparisonColumn`, uma por lado.

## 6. Trechos comentados

```java
// CompareService.java — por que "melhor nos dois critérios" e não uma
// média ponderada dos dois: entrega e latência medem coisas diferentes
// (quantas mensagens chegam vs quão rápido chegam) e o peso relativo
// depende do caso de uso de quem está comparando. Impor um peso fixo
// aqui esconderia essa escolha em vez de deixá-la explícita ao leitor.
if (okA >= okB && p95A <= p95B) {
    verdict = format("“%s” se comporta melhor nos dois critérios.", nameA);
} else if (okA < okB && p95A > p95B) {
    verdict = format("“%s” se comporta melhor nos dois critérios.", nameB);
} else {
    verdict = "Cada um vence em um critério: escolha conforme o que importa mais, entrega ou latência.";
}
```

```tsx
// ComparisonColumn.tsx — por que não existe um "ComparisonKpiGrid":
// buildKpis(scenario, run, ticks) já é uma função pura da Fase 3;
// chamá-la duas vezes (uma por RunReport) é suficiente. Duplicar o
// componente só para "modo comparação" teria dividido a manutenção
// dos KPIs em dois lugares que precisariam mudar sempre juntos.
<KpiGrid kpis={buildKpis(scenario, run, ticks)} />
```

## 7. Como testar manualmente

```sh
TOKEN=$(curl -s -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}' | jq -r .token)

RUN_A=$(curl -s -X POST "http://localhost:1337/api/scenarios/$SCENARIO_A/runs?mode=INSTANT" -H "Authorization: Bearer $TOKEN" | jq -r .id)
RUN_B=$(curl -s -X POST "http://localhost:1337/api/scenarios/$SCENARIO_B/runs?mode=INSTANT" -H "Authorization: Bearer $TOKEN" | jq -r .id)
curl -s "http://localhost:1337/api/runs/compare?a=$RUN_A&b=$RUN_B" -H "Authorization: Bearer $TOKEN"
```

Na UI: `/compare` → escolher dois cenários diferentes → "Comparar" → confirmar que as duas colunas mostram KPIs e gráficos coerentes com cada cenário, e que o parágrafo no topo cita os dois pelo nome com um veredito.

## 8. O que eu aprendi / erros cometidos

- Validado no navegador com dois cenários Kafka de configurações diferentes: o parágrafo comparativo citou corretamente o vencedor ("‘Kafka healthy (cópia)’ se comporta melhor nos dois critérios") e as duas colunas renderizaram com os componentes exatos da Fase 3, sem nenhum ajuste visual necessário — sinal de que a composição por reuso (em vez de duplicar componentes "modo comparação") funcionou como esperado.
- A rota `/api/runs/compare` colidiria em teoria com `/api/runs/{runId}` (ambas casam com `GET /api/runs/algo`), mas o Spring MVC resolve isso sozinho: padrões literais (`compare`) têm prioridade sobre segmentos de variável (`{runId}`) na hora de rotear, então não foi preciso reordenar declarações nem adicionar um prefixo diferente.
