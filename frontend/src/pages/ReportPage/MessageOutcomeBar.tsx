import { Card, Space, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';
import type { RunSummary } from '../../api/modules/types';
import { formatCount } from '../../lib/format';

export function MessageOutcomeBar({ run }: { run: RunSummary }) {
  const { mode, semantic } = useTheme();
  const palette = semantic[mode];
  const total = Math.max(1, run.producedTotal);
  const pending = Math.max(
    0,
    run.producedTotal - run.deliveredTotal - run.dlqTotal - run.droppedTotal,
  );

  const segments = [
    { label: 'Entregues', value: run.deliveredTotal, color: palette.ok.text },
    { label: 'DLQ', value: run.dlqTotal, color: palette.danger.text },
    { label: 'Descartadas', value: run.droppedTotal, color: '#B44BFF' },
    { label: 'Pendentes', value: pending, color: '#4A525C' },
  ].filter((segment) => segment.value > 0);

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Destino das mensagens"
          tip="Para onde foi cada mensagem produzida: entregue, DLQ, descartada por fila cheia ou ainda esperando. Retries são tentativas extras, contabilizadas à parte."
        />
      }
    >
      <div style={{ display: 'flex', height: 24, borderRadius: 6, overflow: 'hidden' }}>
        {segments.map((segment) => (
          <div
            key={segment.label}
            style={{ width: `${(segment.value / total) * 100}%`, background: segment.color }}
            title={`${segment.label}: ${formatCount(segment.value)}`}
          />
        ))}
      </div>
      <Space wrap style={{ marginTop: 8 }}>
        {segments.map((segment) => (
          <Typography.Text key={segment.label} style={{ fontSize: 12 }}>
            <span
              style={{
                display: 'inline-block',
                width: 8,
                height: 8,
                borderRadius: 2,
                background: segment.color,
                marginRight: 4,
              }}
            />
            {segment.label}: {formatCount(segment.value)}
          </Typography.Text>
        ))}
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Retries: {formatCount(run.retriesTotal)}
        </Typography.Text>
      </Space>
    </Card>
  );
}
