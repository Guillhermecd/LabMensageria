import { TrophyOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Row, Slider, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import { RunService } from '../../api/modules/run.service';
import type { BrokerDecision, Decision, ScoreWeights } from '../../api/modules/types';
import { HelpLabel } from '../../components/ui/HelpLabel';

const ROUNDS = 30;
const DEFAULT_WEIGHTS: ScoreWeights = { stability: 35, latency: 25, loss: 20, cost: 10, ops: 10 };
const WEIGHT_LABELS: Record<keyof ScoreWeights, string> = {
  stability: 'Estabilidade',
  latency: 'Latência p99',
  loss: 'Perda de mensagens',
  cost: 'Custo',
  ops: 'Simplicidade operacional',
};
const BROKER_LABEL: Record<string, string> = { KAFKA: 'Kafka', RABBITMQ: 'RabbitMQ', SQS: 'SQS' };
// beyond this the analytical estimate and the simulation disagree enough to distrust the model
const MODEL_ERROR_WARN_PCT = 25;

export function DecisionPanel({ scenarioId, disabled }: { scenarioId: string; disabled: boolean }) {
  const [weights, setWeights] = useState<ScoreWeights>(DEFAULT_WEIGHTS);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const total = Object.values(weights).reduce((sum, value) => sum + value, 0);

  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setDecision(await RunService.decision(scenarioId, weights, ROUNDS));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao comparar os brokers');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Decisão: qual broker?"
          tip={`Roda ${ROUNDS} rodadas simuladas por broker (mesmas sementes para os três) e pontua cada uma com os pesos abaixo. Só declara vencedor se a diferença for material (≥ 5 pontos) e o líder vencer em ≥ 90% das rodadas; senão é empate técnico. Os pesos são relativos: podem somar qualquer valor.`}
        />
      }
      extra={
        <Button
          icon={<TrophyOutlined />}
          onClick={run}
          loading={loading}
          disabled={disabled || total <= 0}
        >
          Comparar brokers
        </Button>
      }
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <Row gutter={[16, 0]}>
          {(Object.keys(weights) as (keyof ScoreWeights)[]).map((key) => (
            <Col key={key} xs={24} md={12} xl={8}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {WEIGHT_LABELS[key]}: {total > 0 ? Math.round((weights[key] / total) * 100) : 0}%
              </Typography.Text>
              <Slider
                min={0}
                max={100}
                value={weights[key]}
                onChange={(value) => setWeights((prev) => ({ ...prev, [key]: value }))}
              />
            </Col>
          ))}
        </Row>

        {error && <Alert type="error" message={error} />}
        {decision && (
          <>
            <Alert
              type={decision.tie ? 'warning' : 'success'}
              showIcon
              message={decision.tie ? 'Empate técnico' : `Vencedor: ${BROKER_LABEL[decision.leader]}`}
              description={decision.verdict}
            />
            <DecisionTable decision={decision} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {decision.rounds} rodadas pareadas · semente-mestre {decision.masterSeed}. Faixa de
              pontos = p10–p90 das rodadas. Erro do modelo = média do desvio do p50 e do p99 do
              modelo analítico frente à mediana simulada — não mede o mundo real, só a consistência
              entre os dois modelos do simulador.
            </Typography.Text>
          </>
        )}
      </Space>
    </Card>
  );
}

function DecisionTable({ decision }: { decision: Decision }) {
  const sorted = [...decision.brokers].sort((a, b) => b.scoreMedian - a.scoreMedian);
  return (
    <Table<BrokerDecision>
      size="small"
      pagination={false}
      rowKey="broker"
      dataSource={sorted}
      scroll={{ x: true }}
      columns={[
        {
          title: 'Broker',
          render: (_, row) => (
            <Space size={4}>
              {BROKER_LABEL[row.broker]}
              {row.broker === decision.leader && !decision.tie && <Tag color="success">líder</Tag>}
              {decision.tie &&
                (row.broker === decision.leader || row.broker === decision.runnerUp) && (
                  <Tag color="warning">empate</Tag>
                )}
            </Space>
          ),
        },
        {
          title: 'Pontos (mediana)',
          render: (_, row) =>
            `${row.scoreMedian.toFixed(1)}  (${row.scoreLow.toFixed(1)}–${row.scoreHigh.toFixed(1)})`,
        },
        {
          title: 'Estab. / Lat. / Perda / Custo / Ops',
          render: (_, row) =>
            [
              row.points.stability,
              row.points.latency,
              row.points.loss,
              row.points.cost,
              row.points.ops,
            ]
              .map((v) => v.toFixed(1))
              .join(' / '),
        },
        {
          title: 'p99 modelo × simulado',
          render: (_, row) => `${Math.round(row.modelP99Ms)} × ${Math.round(row.simP99Ms)} ms`,
        },
        {
          title: 'Erro do modelo',
          render: (_, row) => (
            <Tag color={row.modelErrorPct > MODEL_ERROR_WARN_PCT ? 'error' : 'default'}>
              {row.modelErrorPct.toFixed(0)}%
            </Tag>
          ),
        },
      ]}
    />
  );
}
