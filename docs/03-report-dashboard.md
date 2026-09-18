# 03 — Dashboard de resultados

## 1. Objetivo

Mostrar o relatório completo de uma execução instantânea (KPIs, gráficos, timeline de eventos) na mesma tela onde o cenário é editado — sem navegação — em claro e escuro.

## 2. Conceito de mensageria estudado

**Por que percentis (p50/p95/p99) e não média** na latência: a média esconde a cauda lenta. Um sistema pode ter latência média de 30ms e ainda assim ter 5% das mensagens levando 2 segundos — é justamente esse 1%-5% mais lento (afetado por retries, espera na fila, backpressure) que degrada a experiência de quem depende da mensagem. p50 mostra o caso comum; p95/p99 mostram o que dá errado quando dá errado.

**Utilização vs. capacidade × carga** são a mesma informação vista de dois ângulos: utilização é "quão ocupado" (0–100%), capacidade×carga é "quantas vezes a produção cabe na capacidade" (0×–∞×). Acima de 1× o sistema é matematicamente instável — a fila cresce sem parar, não importa por quanto tempo se espere.

## 3. Decisões de design

- **Layout de uma tela só, sidebar fixa + relatório sempre visível** — não duas páginas navegáveis. A primeira versão desta fase criou uma rota separada (`/scenarios/:id/report`) com um botão "Ver relatório"; foi corrigida para o layout de grid `340px sidebar | 1fr relatório` porque editar parâmetro e ver o efeito no relatório é o fluxo central do produto (mudar cenário → rodar → comparar), e forçar uma navegação a cada ciclo quebra esse loop.
- **`ReportPanel` recebe `scenarioId` por prop, não por rota.** Extrai a lógica de `useParams` para fora do componente de relatório, que passa a ser reutilizável tanto embutido (uso atual) quanto atrás de uma rota própria no futuro, se necessário.
- **Tom semântico (`SemanticTone`) em vez de cor bruta nos KPIs.** `reportMetrics.buildKpis` retorna `tone?: 'ok' | 'warning' | 'danger'`, não uma string de cor. `StatCard` resolve a cor de fato via `useTheme().semantic[mode]`, então o mesmo KPI já nasce correto em claro e escuro sem `if (mode === 'dark')` espalhado pelos componentes.
- **`@ant-design/plots` com `theme` explícito por modo.** O componente `Line` não herda automaticamente o tema do `ConfigProvider` do AntD — precisa do prop `theme={mode === 'dark' ? 'classicDark' : 'classic'}` em cada gráfico, senão o texto dos eixos/legendas usa a paleta clara por padrão (ver seção 8).
- **Utilização por consumidor é uma aproximação, documentada como tal.** O backend não rastreia utilização por consumidor individual (só o agregado por tick). O componente `ConsumerUtilization` desenha N barras (uma por consumidor do cenário) todas com o mesmo valor — a média do último tick — marcando como ociosos os que excedem o paralelismo do broker (Kafka: `consumers > partitions`). É uma simplificação honesta, não uma medição granular.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `api/modules/run.service.ts` | Único ponto de chamada HTTP do domínio de execuções | `api` central |
| `pages/ReportPage/useRunReport.ts` | Hook de página: carrega o cenário, dispara a run, guarda ticks/eventos | `ScenarioService`, `RunService` |
| `pages/ReportPage/reportMetrics.ts` | Função pura que deriva os 7 KPIs a partir de ticks + totais da run | — |
| `pages/ReportPage/ReportPanel.tsx` | Orquestra botão de rodar, KPIs, gráficos e timeline | `useRunReport`, `buildKpis` |
| `pages/ReportPage/{Throughput,Backlog,Latency}Chart.tsx` | Um gráfico de linha cada, via `@ant-design/plots` | `Tick[]` |
| `pages/ReportPage/ConsumerUtilization.tsx` | Barras de utilização por consumidor (aproximação) | `Scenario`, `Tick[]` |
| `pages/ReportPage/MessageOutcomeBar.tsx` | Barra empilhada: entregues/DLQ/descartadas/pendentes | `RunSummary` |
| `pages/ReportPage/EventTimeline.tsx` | Lista reversa dos eventos detectados pelo motor | `SimulationEvent[]` |
| `components/ui/StatCard.tsx` | Card de KPI genérico (label + valor + tom semântico) | `useTheme` |
| `pages/ScenariosPage/ScenariosPage.tsx` | Layout de grid: sidebar (lista+form) + `ReportPanel` | — |

## 5. Fluxo de uma requisição

1. `ScenariosPage` mantém `selectedId`; ao trocar de cenário, `<ReportPanel key={selected.id} .../>` remonta (a `key` força um estado limpo — não faz sentido mostrar o relatório do cenário anterior).
2. `useRunReport(scenarioId)` busca o cenário (`ScenarioService.get`) assim que monta.
3. Clique em "Resultado instantâneo" → `RunService.runInstant` (POST) → em paralelo, `RunService.listTicks` + `listEvents` (`Promise.all`) assim que a run retorna criada.
4. `reportMetrics.buildKpis(scenario, run, ticks)` deriva os 7 cards a partir dos dados já carregados — nenhuma chamada extra ao backend para os KPIs.

## 6. Trechos comentados

```tsx
// StatCard.tsx — por que o tom vem de fora, não de uma cor fixa:
// o mesmo KPI ("Utilização: 92%") deve ficar vermelho tanto no claro
// quanto no escuro, mas o vermelho exato é diferente em cada tema.
// Resolver a cor aqui (não em reportMetrics) mantém a lógica de negócio
// (quando é "ruim") separada da apresentação (qual pixel pintar).
const { mode, semantic } = useTheme();
const color = tone ? semantic[mode][tone].text : undefined;
```

```tsx
// ThroughputChart.tsx — por que o tema do gráfico é explícito:
// @ant-design/plots renderiza em <canvas>/SVG próprio, fora da árvore
// de CSS-in-JS do AntD. Herdar o ConfigProvider não é automático;
// sem o prop `theme`, o texto dos eixos usa a paleta clara sempre,
// ficando ilegível (texto escuro) sobre o fundo escuro do app.
<Line theme={mode === 'dark' ? 'classicDark' : 'classic'} ... />
```

## 7. Como testar manualmente

1. `docker compose -f docker-compose.dev.yml up -d postgres minio mailpit`, subir backend e `cd frontend && npm run dev`.
2. Login → selecionar um cenário na sidebar → clicar "Resultado instantâneo".
3. Conferir: os 7 KPIs aparecem com valores coerentes com o cenário; os 3 gráficos de linha mostram uma série por segundo; a barra de utilização mostra uma barra por consumidor; a linha do tempo lista os eventos do mais recente para o mais antigo.
4. Trocar o tema (se exposto na UI) ou alternar `prefers-color-scheme` do SO e confirmar que texto e gráficos continuam legíveis nos dois modos.
5. Trocar de cenário na sidebar e confirmar que o relatório anterior desaparece (não mistura dados de cenários diferentes).

## 8. O que eu aprendi / erros cometidos

- **Erro de arquitetura pego só depois de testar no navegador**: a primeira versão desta fase implementou o relatório como uma página/rota separada (`/scenarios/:id/report`), exigindo clique em "Ver relatório" e navegação. Isso divergia do protótipo original, que é uma tela única com sidebar fixa (grid `300px 1fr`) — editar parâmetro e ver o efeito no relatório é o mesmo gesto, sem navegação. Corrigido restruturando `ScenariosPage` para o grid de duas colunas e extraindo `ReportPage` em `ReportPanel` (recebe `scenarioId` por prop, sem rota própria).
- **Bug real de contraste, não só estético**: ao trocar o `<Layout>` do AntD por `<div>`/`<aside>`/`<main>` puros durante essa refatoração, perdi o `background` que o `Layout` aplicava implicitamente via token `colorBgLayout`. Resultado: texto branco (tema escuro) sobre o branco padrão do navegador — completamente ilegível, mas presente no DOM (confirmado via árvore de acessibilidade, só não visível na tela). Corrigido aplicando `colorBgLayout`/`colorBgBase` explicitamente nos containers. Lição: qualquer container de página que não seja `<Layout>` do AntD precisa de background explícito — o `ConfigProvider` não pinta o `<body>` sozinho.
- **`@ant-design/plots` não herda tema do `ConfigProvider`**: precisa do prop `theme` em cada gráfico. Sem isso, os gráficos renderizam (dados corretos) mas com texto de eixo/legenda ilegível no modo escuro — outro caso do mesmo bug de contraste, só que localizado no componente de terceiros em vez do CSS do app.
- AntD v6 depreciou `Space.direction` → `orientation`, `Spin.tip` → `description`, `Timeline.items.children` → `items.content`; corrigidos nesta fase para não carregar warnings de depreciação adiante.
