import { Line } from '@ant-design/plots';
import { Card, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';
import type { Scenario, Tick } from '../../api/modules/types';

export function BacklogChart({ scenario, ticks }: { scenario: Scenario; ticks: Tick[] }) {
  const { mode } = useTheme();
  const data = ticks.map((tick) => ({ second: tick.second, backlog: tick.backlog }));
  const hasQueueLimit =
    scenario.broker === 'RABBITMQ' && !!scenario.queueCapacity && scenario.queueCapacity > 0;

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Backlog"
          tip="Mensagens aguardando na fila a cada segundo. Backlog crescente significa consumo abaixo da produção."
        />
      }
    >
      {ticks.length === 0 ? (
        <Typography.Text type="secondary">Sem dados ainda.</Typography.Text>
      ) : (
        <>
          <Line
            data={data}
            xField="second"
            yField="backlog"
            theme={mode === 'dark' ? 'classicDark' : 'classic'}
            height={180}
            axis={{ x: { title: 'segundo' }, y: { title: 'mensagens' } }}
            style={{ stroke: '#F5A524' }}
            area={{ style: { fillOpacity: 0.15 } }}
          />
          {hasQueueLimit && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Capacidade máxima da fila: {scenario.queueCapacity} mensagens — acima disso o RabbitMQ
              descarta as mais antigas.
            </Typography.Text>
          )}
        </>
      )}
    </Card>
  );
}
