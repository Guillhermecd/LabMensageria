import type { RunSummary, Scenario, Tick } from '../../api/modules/types';
import { formatCount } from '../../lib/format';
import type { SemanticTone } from '../../theme';

export type Kpi = {
  label: string;
  value: string;
  sub: string;
  tone?: SemanticTone;
  tip: string;
};

function average(ticks: Tick[], key: keyof Tick): number {
  if (ticks.length === 0) return 0;
  return ticks.reduce((sum, tick) => sum + (tick[key] as number), 0) / ticks.length;
}

function max(ticks: Tick[], key: keyof Tick): number {
  if (ticks.length === 0) return 0;
  return Math.max(...ticks.map((tick) => tick[key] as number));
}

/** Mirrors RunStatistics.warmupSeconds on the backend: the empty-queue start is excluded from stats. */
export function warmupSeconds(durationSeconds: number): number {
  return Math.min(Math.max(5, Math.floor(durationSeconds / 10)), Math.floor(durationSeconds / 2));
}

export function buildKpis(scenario: Scenario, run: RunSummary, allTicks: Tick[]): Kpi[] {
  const warmup = warmupSeconds(scenario.durationSeconds);
  const steady = allTicks.filter((tick) => tick.second >= warmup);
  // while a live run is still inside the warm-up window there is nothing steady to summarise yet
  const ticks = steady.length > 0 ? steady : allTicks;
  const last = allTicks[allTicks.length - 1];
  const util = average(ticks, 'utilization');
  const backlogMax = max(ticks, 'backlog');
  const capacity = average(ticks, 'capacity');
  const consumedSum = ticks.reduce((sum, tick) => sum + tick.consumed, 0);
  const failedSum = ticks.reduce((sum, tick) => sum + tick.failed, 0);
  const usefulShare = consumedSum + failedSum > 0 ? consumedSum / (consumedSum + failedSum) : 1;
  const usefulCapacity = capacity * usefulShare;
  const lossPct =
    run.producedTotal > 0 ? ((run.dlqTotal + run.droppedTotal) / run.producedTotal) * 100 : 0;
  const ratio = capacity > 0 ? scenario.ratePerSecond / capacity : 0;
  const hasLoss = run.dlqTotal + run.droppedTotal > 0;

  return [
    {
      label: 'Throughput médio',
      value: `${formatCount(average(ticks, 'consumed'))}/s`,
      sub: `produção ${formatCount(average(ticks, 'produced'))}/s`,
      tip: 'Mensagens processadas com sucesso por segundo, em média. Compare com a produção: se for menor, a fila está acumulando.',
    },
    {
      label: 'Latência p50 / p95',
      value: `${Math.round(average(ticks, 'p50Ms'))} / ${Math.round(average(ticks, 'p95Ms'))}`,
      sub: `p99 ${Math.round(average(ticks, 'p99Ms'))} ms · média sem aquecimento`,
      tone: average(ticks, 'p95Ms') > 1000 ? 'warning' : undefined,
      tip: `Tempo de publicar até concluir, em média ao longo da execução, sem os primeiros ${warmup}s (aquecimento: a fila começa vazia e a latência inicial é otimista). p50: metade das mensagens é mais rápida que isso. p95/p99: os 5%/1% mais lentos, onde espera na fila e retries aparecem. Uma única execução é uma amostra: use “Rodar N rodadas” para ver a faixa.`,
    },
    {
      label: 'Backlog atual',
      value: formatCount(last?.backlog ?? 0),
      sub: `pico ${formatCount(backlogMax)}`,
      tone: last && last.backlog > scenario.ratePerSecond * 5 ? 'warning' : undefined,
      tip: 'Mensagens esperando na fila neste instante (no Kafka, chamado de lag). Cresce quando a produção supera o consumo.',
    },
    {
      label: 'DLQ / perdidas',
      value: formatCount(run.dlqTotal + run.droppedTotal),
      sub: `${lossPct.toFixed(2)}% do total`,
      tone: hasLoss ? 'danger' : 'ok',
      tip: 'Mensagens que não foram entregues: esgotaram os retries (DLQ) ou foram descartadas por fila cheia.',
    },
    {
      label: 'Retries',
      value: formatCount(run.retriesTotal),
      sub:
        run.producedTotal > 0
          ? `${((run.retriesTotal / run.producedTotal) * 100).toFixed(1)}% das msgs`
          : '—',
      tip: 'Tentativas extras de processamento após falha. Consomem capacidade dos consumidores e alongam a cauda de latência.',
    },
    {
      label: 'Utilização',
      value: `${Math.round(util * 100)}%`,
      sub: `capacidade ~${Math.round(capacity)}/s`,
      tone: util > 0.9 ? 'danger' : util > 0.7 ? 'warning' : 'ok',
      tip: 'Fração do tempo em que os consumidores estão ocupados. Acima de ~80% qualquer pico vira backlog; 100% = saturado.',
    },
    {
      label: 'Capacidade bruta × útil',
      value: `${Math.round(capacity)} → ${Math.round(usefulCapacity)}/s`,
      sub: `${Math.round((1 - usefulShare) * 100)}% gasto em tentativas que falharam`,
      tone: usefulShare < 0.9 ? 'warning' : undefined,
      tip: 'Bruta: consumidores × 1000/tempo de serviço. Útil: descontando o slot ocupado por tentativas que falham e serão repetidas. Com taxa de falha alta a capacidade real fica bem abaixo da bruta.',
    },
    {
      label: 'Capacidade × carga',
      value: `${ratio.toFixed(2)}×`,
      sub: ratio > 1 ? 'carga acima da capacidade' : `folga de ${Math.round((1 - ratio) * 100)}%`,
      tone: ratio > 1 ? 'danger' : 'ok',
      tip: 'Produção dividida pela capacidade média dos consumidores. Acima de 1× o sistema é instável: a fila cresce sem limite.',
    },
  ];
}

/** Grey band over the warm-up window, shared by the time-series charts. */
export function warmupBand(warmup: number) {
  return [{ type: 'rangeX', data: [[0, warmup]], style: { fill: '#8B939E', fillOpacity: 0.15 } }];
}
