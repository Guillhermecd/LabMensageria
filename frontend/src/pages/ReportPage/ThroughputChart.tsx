import { Line } from '@ant-design/plots';
import { Card, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';
import type { Tick } from '../../api/modules/types';

const SERIES_COLOR = { Produzidas: '#7C8CFF', Processadas: '#22D3A0', Falhas: '#FF6B4A' };

export function ThroughputChart({ ticks }: { ticks: Tick[] }) {
  const { mode } = useTheme();
  const data = ticks.flatMap((tick) => [
    { second: tick.second, series: 'Produzidas', value: tick.produced },
    { second: tick.second, series: 'Processadas', value: tick.consumed },
    { second: tick.second, series: 'Falhas', value: tick.failed },
  ]);

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Throughput"
          tip="Linha azul: mensagens publicadas por segundo. Verde: processadas com sucesso. Vermelha: falhas. Quando o azul fica acima do verde, a fila acumula."
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
          axis={{ x: { title: 'segundo' }, y: { title: 'msg/s' } }}
        />
      )}
    </Card>
  );
}
