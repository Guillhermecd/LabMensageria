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

export function buildKpis(scenario: Scenario, run: RunSummary, ticks: Tick[]): Kpi[] {
  const last = ticks[ticks.length - 1];
  const util = average(ticks, 'utilization');
  const backlogMax = max(ticks, 'backlog');
  const capacity = average(ticks, 'capacity');
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
      value: `${last?.p50Ms ?? 0} / ${last?.p95Ms ?? 0}`,
      sub: `p99 ${last?.p99Ms ?? 0} ms · final`,
      tone: last && last.p95Ms > 1000 ? 'warning' : undefined,
      tip: 'Tempo de publicar até concluir. p50: metade das mensagens é mais rápida que isso. p95/p99: os 5%/1% mais lentos, onde espera na fila e retries aparecem.',
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
      label: 'Capacidade × carga',
      value: `${ratio.toFixed(2)}×`,
      sub: ratio > 1 ? 'carga acima da capacidade' : `folga de ${Math.round((1 - ratio) * 100)}%`,
      tone: ratio > 1 ? 'danger' : 'ok',
      tip: 'Produção dividida pela capacidade média dos consumidores. Acima de 1× o sistema é instável: a fila cresce sem limite.',
    },
  ];
}
