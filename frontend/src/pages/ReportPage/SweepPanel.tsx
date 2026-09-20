import { LineChartOutlined } from '@ant-design/icons';
import { Line } from '@ant-design/plots';
import { Alert, Button, Card, Typography } from 'antd';
import { useState } from 'react';
import { RunService } from '../../api/modules/run.service';
import type { Sweep } from '../../api/modules/types';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';

const ROUNDS = 20;
const BROKER_LABEL: Record<string, string> = { KAFKA: 'Kafka', RABBITMQ: 'RabbitMQ', SQS: 'SQS' };

export function SweepPanel({ scenarioId, disabled }: { scenarioId: string; disabled: boolean }) {
  const { mode } = useTheme();
  const [sweep, setSweep] = useState<Sweep | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setSweep(await RunService.sweep(scenarioId, ROUNDS));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao rodar a varredura');
    } finally {
      setLoading(false);
    }
  };

  const data = (sweep?.brokers ?? []).flatMap((broker) =>
    broker.points.map((point) => ({
      occupancy: `${point.occupancyPct}%`,
      broker: BROKER_LABEL[broker.broker],
      p99: Math.round(point.p99MedianMs),
    })),
  );

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Latência × ocupação"
          tip={`Sobe a carga para 50%, 75%, 90% e 95% da capacidade de cada broker e mede o p99 (mediana de ${ROUNDS} rodadas). A curva é convexa: perto da saturação uma pequena alta de carga multiplica a latência. Use para escolher a folga de dimensionamento.`}
        />
      }
      extra={
        <Button icon={<LineChartOutlined />} onClick={run} loading={loading} disabled={disabled}>
          Varrer ocupação
        </Button>
      }
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      {!sweep && !loading && !error && (
        <Typography.Text type="secondary">
          Mostra como o p99 explode conforme a carga se aproxima da capacidade.
        </Typography.Text>
      )}
      {sweep && (
        <Line
          data={data}
          xField="occupancy"
          yField="p99"
          colorField="broker"
          theme={mode === 'dark' ? 'classicDark' : 'classic'}
          height={240}
          axis={{ x: { title: 'ocupação da capacidade' }, y: { title: 'p99 (ms)' } }}
        />
      )}
    </Card>
  );
}
