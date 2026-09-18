import { Button, Card, List, Space, Tag, Typography } from 'antd';
import type { RunSummary } from '../../api/modules/types';

const STATUS_COLOR: Record<RunSummary['status'], string> = {
  COMPLETED: 'success',
  STOPPED: 'warning',
  RUNNING: 'processing',
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('pt-BR');
}

export function RunHistory({
  history,
  activeRunId,
  onReopen,
}: {
  history: RunSummary[];
  activeRunId: string | null;
  onReopen: (runId: string) => void;
}) {
  return (
    <Card size="small" title="Histórico de execuções" style={{ marginBottom: 24 }}>
      <List
        dataSource={history}
        renderItem={(run) => (
          <List.Item
            actions={[
              <Button
                key="reopen"
                size="small"
                type={run.id === activeRunId ? 'default' : 'link'}
                disabled={run.id === activeRunId}
                onClick={() => onReopen(run.id)}
              >
                {run.id === activeRunId ? 'Aberta' : 'Reabrir'}
              </Button>,
            ]}
          >
            <Space size={8}>
              <Tag color={STATUS_COLOR[run.status]}>{run.status}</Tag>
              <Tag>{run.mode}</Tag>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {formatDate(run.startedAt)} · {run.deliveredTotal}/{run.producedTotal} entregues
              </Typography.Text>
            </Space>
          </List.Item>
        )}
      />
    </Card>
  );
}
