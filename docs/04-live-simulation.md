# 04 — Execução ao vivo

## 1. Objetivo

Rodar um cenário e ver o relatório crescer em tempo real (tick a tick), com controle de "Rodar ao vivo" / "Parar" e barra de progresso — sem o usuário precisar recarregar nada.

## 2. Conceito de mensageria estudado

**SSE vs. WebSocket vs. polling**, na prática deste projeto:

- **Polling** (cliente pergunta "tem tick novo?" a cada N ms) desperdiça requisições quando nada mudou e sempre atrasa em até um intervalo inteiro.
- **WebSocket** é bidirecional — útil quando o cliente também precisa mandar dados pelo mesmo canal em tempo real. Aqui o cliente só manda um comando ocasional (`stop`), que cabe perfeitamente em uma requisição HTTP comum.
- **SSE** é HTTP simples com reconexão nativa do browser (`EventSource`) e apenas uma direção (servidor → cliente) — exatamente o formato dos dados aqui: uma sequência de ticks e eventos que só o servidor produz.

Ver ADR 0002 para a decisão completa e os trade-offs assumidos.

## 3. Decisões de design

- **`LiveSimulationService` não reimplementa o motor** — reaproveita o mesmo `SimulationEngine.step()` da Fase 2. A única diferença entre instantâneo e ao vivo é *quando* cada tick é entregue: tudo de uma vez (instantâneo) ou indo pela rede assim que é calculado (ao vivo). Nenhuma regra de broker/evento foi duplicada.
- **`stop` é uma flag, não uma interrupção de thread.** `AtomicBoolean stopRequested` é conferida entre ticks, nunca no meio de um `engine.step()`. Isso garante que a run pare num estado consistente — sempre com o mesmo número de ticks computados e persistidos, nunca "metade de um tick".
- **Persistência em lote também no modo ao vivo** (`TICK_FLUSH_BATCH = 10`), não tick a tick. Gravar a cada tick individual geraria uma escrita ao banco a cada 100ms por simulação ativa; agrupar de 10 em 10 (mais um flush final ao terminar/parar) reduz o volume de escritas sem atrasar visivelmente o que o usuário vê — a UI recebe cada tick via SSE independentemente da cadência de persistência.
- **`SimulationPersistence` extraído da Fase 2** especificamente para esta fase: `SimulationRunService` (instantâneo) e `LiveSimulationService` (ao vivo) precisavam da mesma conversão tick/evento → entidade e do mesmo fechamento de totais (`producedTotal`, `deliveredTotal` etc). Duplicar esse código nos dois serviços teria violado DRY logo na primeira reutilização.
- **Autenticação da stream via query param, isolada no filtro.** `JwtAuthenticationFilter` só cai no fallback de query param quando não há header `Authorization` — todas as outras rotas continuam exigindo o header normalmente. Ver ADR 0002 para o porquê dessa exceção existir.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `service/simulation/LiveSimulationService` | Um loop por run ativa; emite SSE, persiste em lote, atende `stop` | `SimulationEngine`, `SimulationPersistence` |
| `service/simulation/SimulationPersistence` | Conversão tick/evento → entidade e fechamento de totais (compartilhado) | Repositórios de run/tick/event |
| `security/JwtAuthenticationFilter` | Aceita JWT via header ou `?token=` (só assim o `EventSource` autentica) | `JwtService` |
| `controller/RunController` | `GET /runs/{id}/stream`, `POST /runs/{id}/stop`, `mode=LIVE` no create | `LiveSimulationService` |
| `pages/ReportPage/useRunReport.ts` | Um hook só para os dois modos — `runInstant` e `runLive` escrevem no mesmo estado (`ticks`, `events`) | `EventSource`, `RunService` |
| `pages/ReportPage/ReportPanel.tsx` | Botões "Rodar ao vivo" / "Parar", barra de progresso durante execução ao vivo | `useRunReport` |

## 5. Fluxo de uma requisição

`GET /api/runs/{runId}/stream?speed=5`:

1. `RunController.stream` delega para `LiveSimulationService.stream`, que confirma dono da run, `mode=LIVE` e `status=RUNNING` antes de abrir o `SseEmitter`.
2. O emitter é devolvido **imediatamente** — a computação roda numa thread separada (`ExecutorService`), não na thread da requisição.
3. A cada iteração: `speed` chamadas a `engine.step()`, depois um evento SSE `tick`/`event` por resultado novo, depois checagem se é hora de persistir (a cada 10 ticks) e checagem de `stopRequested`.
4. Ao terminar (naturalmente ou por `stop`), persiste o que sobrou, marca a run como `COMPLETED` ou `STOPPED`, emite um evento `finished` com o resumo, e fecha o emitter.

Frontend (`useRunReport.runLive`):

1. `POST /scenarios/{id}/runs?mode=LIVE` cria a run (`status=RUNNING`) e retorna o `runId`.
2. Abre `new EventSource(streamUrl(runId, speed))` — a mesma requisição já inclui o token na URL.
3. `addEventListener('tick', ...)` acumula em `ticks` (mesmo formato usado pelo modo instantâneo — os componentes de gráfico não sabem se os dados vieram de uma vez ou aos poucos).
4. `addEventListener('finished', ...)` fecha a conexão e atualiza o resumo da run com os totais definitivos.

## 6. Trechos comentados

```java
// LiveSimulationService.java — por que a flag de stop é conferida
// ENTRE ticks, nunca dentro de engine.step(): interromper no meio de
// um tick deixaria o tick parcialmente computado (produced somado,
// mas ok/failed ainda não) — o estado do motor ficaria inconsistente.
while (!state.isDone() && !stopRequested.get()) {
    for (int i = 0; i < speed && !state.isDone(); i++) {
        engine.step(state, scenario);
    }
    // ... emite e persiste só depois do step completo
}
```

```ts
// useRunReport.ts — por que runInstant e runLive escrevem no mesmo
// `ticks`/`events`: os componentes de gráfico (ThroughputChart,
// BacklogChart...) recebem só `Tick[]`, sem saber a origem. Isso deixa
// o modo ao vivo "de graça" para qualquer gráfico novo que a Fase 5+
// adicionar — não precisa de uma versão "live" de cada componente.
source.addEventListener('tick', (message) => {
  const tick = JSON.parse((message as MessageEvent).data) as Tick;
  setTicks((prev) => [...prev, tick]);
});
```

## 7. Como testar manualmente

```sh
TOKEN=$(curl -s -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}' | jq -r .token)

RUN_ID=$(curl -s -X POST "http://localhost:1337/api/scenarios/$SCENARIO_ID/runs?mode=LIVE" \
  -H "Authorization: Bearer $TOKEN" | jq -r .id)

# -N desliga o buffer do curl, necessário para ver os eventos chegando aos poucos
curl -N "http://localhost:1337/api/runs/$RUN_ID/stream?speed=1&token=$TOKEN"

# em outro terminal, para interromper antes do fim:
curl -X POST "http://localhost:1337/api/runs/$RUN_ID/stop" -H "Authorization: Bearer $TOKEN"
```

Na UI: clicar "Rodar ao vivo" num cenário com duração alta o bastante para observar a barra de progresso e os gráficos crescendo; clicar "Parar" e confirmar que a run fecha com `status=STOPPED` e os dados até aquele ponto continuam no relatório.

## 8. O que eu aprendi / erros cometidos

- Testado ao vivo (literalmente): criei um cenário com `durationSeconds=4000` só para conseguir ver a barra de progresso e o botão "Parar" em ação — com a velocidade padrão (5 ticks a cada 100ms) uma simulação de 120s termina em ~2.4s, rápido demais para observar o streaming a olho nu. Cliquei "Parar" aos ~450s de 4000 e confirmei: `status=STOPPED`, gráficos com dados reais até o ponto exato da parada, nada além disso.
- `SimulationPersistence` só existe porque a Fase 2 tinha colocado a conversão tick/evento→entidade dentro de `SimulationRunService`. Extrair antes de duplicar (em vez de duplicar e refatorar depois) evitou reescrever a mesma lógica duas vezes com uma pequena diferença cada uma.
- O detalhe do token na URL da SSE é uma concessão real, documentada na ADR 0002 — não é um descuido, é a única forma de autenticar `EventSource` nativo sem reescrever o cliente para usar `fetch` com streaming manual (o que perderia a reconexão automática do `EventSource`).
