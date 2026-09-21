import { AppstoreOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { RunService } from '../../api/modules/run.service';
import type { Broker, BrokerDecision, Suite, SuiteVariant } from '../../api/modules/types';
import { HelpLabel } from '../../components/ui/HelpLabel';

const ROUNDS = 20;
const BROKERS: { key: Broker; label: string }[] = [
  { key: 'KAFKA', label: 'Kafka' },
  { key: 'RABBITMQ', label: 'RabbitMQ' },
  { key: 'SQS', label: 'SQS' },
];

function cell(variant: SuiteVariant, broker: Broker) {
  const row: BrokerDecision | undefined = variant.decision.brokers.find((b) => b.broker === broker);
  if (!row) return null;
  const { decision } = variant;
  const isLeader = broker === decision.leader;
  const inTie = decision.tie && (broker === decision.leader || broker === decision.runnerUp);
  return (
    <Space orientation="vertical" size={0}>
      <Space size={4}>
        <b>{row.scoreMedian.toFixed(0)} pts</b>
        {inTie && <Tag color="warning">empate</Tag>}
        {isLeader && !decision.tie && <Tag color="success">líder</Tag>}
      </Space>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        p99 {Math.round(row.simP99Ms)} ms (pior {Math.round(row.simP99WorstMs)})
      </Typography.Text>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        pico backlog {Math.round(row.peakBacklogMedian)} · perda {row.lossPctMedian.toFixed(2)}%
      </Typography.Text>
    </Space>
  );
}

export function SuitePanel({ scenarioId, disabled }: { scenarioId: string; disabled: boolean }) {
  const [suite, setSuite] = useState<Suite | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setSuite(await RunService.suite(scenarioId, ROUNDS));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao rodar a suíte');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Broker × cenário"
          tip={`Roda o mesmo cenário em cinco situações (saudável, metade dos consumidores cai, pico de 2× na produção, 10% de falha, sobrecarga 5×), com ${ROUNDS} rodadas por célula. Cada cenário declara uma ocupação alvo, não uma taxa: a taxa exibida é ocupação × capacidade do broker escolhido no formulário, então a taxa do formulário não interfere aqui. É quando algo quebra que os brokers deixam de empatar. Suposição do modelo: o Kafka para todo o consumo por ~6s ao rebalancear quando um consumidor sai; RabbitMQ e SQS redistribuem sem pausa.`}
        />
      }
      extra={
        <Button icon={<AppstoreOutlined />} onClick={run} loading={loading} disabled={disabled}>
          Rodar suíte completa
        </Button>
      }
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      {!suite && !loading && !error && (
        <Typography.Text type="secondary">
          Compara os três brokers em cenários de falha, não só no caso saudável.
        </Typography.Text>
      )}
      {suite && (
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type={suite.leaderChanges ? 'warning' : 'info'}
            showIcon
            message={suite.summary}
          />
          <Table<SuiteVariant>
            size="small"
            pagination={false}
            rowKey="variant"
            dataSource={suite.variants}
            scroll={{ x: true }}
            columns={[
              {
                title: 'Cenário',
                render: (_: unknown, row: SuiteVariant) => (
                  <Tooltip title={row.detail}>
                    <span>
                      {row.label}{' '}
                      <Typography.Text type="secondary">
                        · {row.ratePerSecond.toLocaleString('pt-BR')} msg/s ({row.occupancyPct}%)
                      </Typography.Text>
                    </span>
                  </Tooltip>
                ),
              },
              ...BROKERS.map((b) => ({
                title: b.label,
                render: (_: unknown, row: SuiteVariant) => cell(row, b.key),
              })),
            ]}
          />
        </Space>
      )}
    </Card>
  );
}
