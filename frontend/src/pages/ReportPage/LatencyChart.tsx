import { Line } from '@ant-design/plots';
import { Card, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';
import type { Scenario, Tick } from '../../api/modules/types';
import { warmupBand, warmupSeconds } from './reportMetrics';

const SERIES_COLOR = { p50: '#22D3A0', p95: '#F5A524', p99: '#FF6B4A' };

export function LatencyChart({ scenario, ticks }: { scenario: Scenario; ticks: Tick[] }) {
  const { mode } = useTheme();
  const warmup = warmupSeconds(scenario.durationSeconds);
  const data = ticks.flatMap((tick) => [
    { second: tick.second, series: 'p50', value: tick.p50Ms },
    { second: tick.second, series: 'p95', value: tick.p95Ms },
    { second: tick.second, series: 'p99', value: tick.p99Ms },
  ]);

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Latência (p50 / p95 / p99)"
          tip="Tempo entre publicar e concluir. p50: metade das mensagens é mais rápida que isso. p95/p99: os 5%/1% mais lentos, onde espera na fila e retries aparecem. Por isso comparamos percentis, não a média: a média esconde a cauda lenta que mais incomoda o usuário."
        />
      }
    >
      {ticks.length === 0 ? (
        <Typography.Text type="secondary">Sem dados ainda.</Typography.Text>
      ) : (
        <Line
          data={data}
          xField="second"
          yField="value"
          colorField="series"
          theme={mode === 'dark' ? 'classicDark' : 'classic'}
          scale={{
            color: { domain: Object.keys(SERIES_COLOR), range: Object.values(SERIES_COLOR) },
          }}
          height={220}
          axis={{ x: { title: 'segundo' }, y: { title: 'ms' } }}
          annotations={warmupBand(warmup)}
        />
      )}
      {ticks.length > 0 && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Faixa cinza: aquecimento (0–{warmup}s), fora das métricas — a fila ainda começa vazia.
        </Typography.Text>
      )}
    </Card>
  );
}
