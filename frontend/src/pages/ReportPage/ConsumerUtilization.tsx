import { Card, Progress, Space, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';
import type { Scenario, Tick } from '../../api/modules/types';

export function ConsumerUtilization({ scenario, ticks }: { scenario: Scenario; ticks: Tick[] }) {
  const { mode, semantic } = useTheme();
  const palette = semantic[mode];
  const last = ticks[ticks.length - 1];
  const util = last ? last.utilization : 0;

  const effectiveConsumers =
    scenario.broker === 'KAFKA' && scenario.partitions
      ? Math.min(scenario.consumers, scenario.partitions)
      : scenario.consumers;

  const consumers = Array.from({ length: scenario.consumers }, (_, i) => i < effectiveConsumers);
  const tone =
    util > 0.9 ? palette.danger.text : util > 0.7 ? palette.warning.text : palette.ok.text;

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Utilização por consumidor"
          tip="Fração do tempo em que cada consumidor está ocupado. Acima de 90% não há folga para picos. Cinza = consumidor ocioso (sem partição atribuída)."
        />
      }
    >
      {ticks.length === 0 ? (
        <Typography.Text type="secondary">Sem dados ainda.</Typography.Text>
      ) : (
        <>
          <Space orientation="vertical" style={{ width: '100%' }} size={4}>
            {consumers.map((active, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Typography.Text type="secondary" style={{ width: 90, fontSize: 12 }}>
                  consumer-{i + 1}
                </Typography.Text>
                <Progress
                  percent={active ? Math.round(util * 100) : 0}
                  strokeColor={active ? tone : '#4A525C'}
                  size="small"
                  style={{ flex: 1 }}
                />
              </div>
            ))}
          </Space>
          {effectiveConsumers < scenario.consumers && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {scenario.consumers - effectiveConsumers} consumidor(es) ociosos: só{' '}
              {scenario.partitions} partições disponíveis no Kafka.
            </Typography.Text>
          )}
        </>
      )}
    </Card>
  );
}
