import { TrophyOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Slider,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';
import { DEFAULT_ROUNDS, RunService } from '../../api/modules/run.service';
import type { BrokerDecision, Decision, ScoreWeights } from '../../api/modules/types';
import { HelpLabel } from '../../components/ui/HelpLabel';
import {
  BROKER_LABEL,
  ceilingText,
  failureModeLabel,
  MODEL_ERROR_WARN_PCT,
  money,
  tiedNames,
} from './analysisHelpers';
import { SeedControl } from './SeedControl';

const ROUNDS = DEFAULT_ROUNDS;
const DEFAULT_WEIGHTS: ScoreWeights = { stability: 35, latency: 25, loss: 20, cost: 10, ops: 10 };
const WEIGHT_LABELS: Record<keyof ScoreWeights, string> = {
  stability: 'Estabilidade',
  latency: 'Latência p99',
  loss: 'Perda de mensagens',
  cost: 'Custo',
  ops: 'Simplicidade operacional',
};

export function DecisionPanel({ scenarioId, disabled }: { scenarioId: string; disabled: boolean }) {
  const [weights, setWeights] = useState<ScoreWeights>(DEFAULT_WEIGHTS);
  const [seed, setSeed] = useState<number | null>(null);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const total = Object.values(weights).reduce((sum, value) => sum + value, 0);

  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setDecision(await RunService.decision(scenarioId, weights, ROUNDS, seed ?? undefined));
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
          tip={`Roda ${ROUNDS} rodadas simuladas por broker (mesmas sementes para os três) e pontua cada uma com os pesos abaixo; nunca mostra uma rodada única. Dois brokers empatam quando a diferença técnica entre eles (estabilidade, latência, perda) é menor que a dispersão entre as rodadas, em qualquer posição do ranking. Custo e operação só desempatam. Os pesos são relativos: podem somar qualquer valor.`}
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
        <SeedControl value={seed} onChange={setSeed} lastSeed={decision?.masterSeed} />

        {error && <Alert type="error" message={error} />}
        {decision && (
          <>
            <Alert
              type={decision.tie ? 'warning' : 'success'}
              showIcon
              message={
                decision.tie ? 'Empate técnico' : `Vencedor: ${BROKER_LABEL[decision.leader]}`
              }
              description={decision.verdict}
            />
            <ModelTrustAlert decision={decision} />
            <DecisionTable decision={decision} />
            <SaturationPanel decision={decision} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {decision.rounds} rodadas pareadas · semente-mestre {decision.masterSeed} (digite-a
              acima para repetir exatamente estas rodadas). Cada número é a mediana das rodadas, com
              o pior caso ao lado; faixa de pontos = p10–p90. Erro do modelo = média do desvio do
              p50 e do p99 do modelo analítico frente à mediana simulada — não mede o mundo real, só
              a consistência entre os dois modelos do simulador.
            </Typography.Text>
          </>
        )}
      </Space>
    </Card>
  );
}

/** A simulator that can say "here I do not know" is worth more than one that always answers. */
function ModelTrustAlert({ decision }: { decision: Decision }) {
  const untrusted = decision.brokers.filter((b) => !b.modelReliable);
  if (untrusted.length === 0) return null;
  const worst = Math.max(...untrusted.map((b) => b.modelErrorPct));
  return (
    <Alert
      type="warning"
      showIcon
      message={`Modelo analítico não confiável nesta carga (erra até ${(1 + worst / 100).toFixed(1)}×)`}
      description={`Acima de ${MODEL_ERROR_WARN_PCT}% de erro em relação à simulação (${untrusted
        .map((b) => BROKER_LABEL[b.broker])
        .join(
          ', ',
        )}): use os números simulados; as estimativas do modelo (cards comparativos) não servem de base para decidir.`}
    />
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
            <Space size={4} wrap>
              {BROKER_LABEL[row.broker]}
              {row.broker === decision.leader && row.tiedWith.length === 0 && (
                <Tag color="success">líder</Tag>
              )}
              {row.tiedWith.length > 0 && <Tag color="warning">empate com {tiedNames(row)}</Tag>}
              {row.saturation && <Tag color="error">saturado</Tag>}
            </Space>
          ),
        },
        {
          title: 'Pontos (mediana · pior)',
          render: (_, row) =>
            `${row.scoreMedian.toFixed(1)} · ${row.scoreWorst.toFixed(1)}  (p10–p90: ${row.scoreLow.toFixed(1)}–${row.scoreHigh.toFixed(1)})`,
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
          title: 'p99 simulado (mediana · p95 · pior)',
          render: (_, row) =>
            row.saturation ? (
              <Typography.Text type="secondary">em saturação — ver abaixo</Typography.Text>
            ) : (
              `${Math.round(row.simP99Ms)} · ${Math.round(row.simP99P95Ms)} · ${Math.round(row.simP99WorstMs)} ms`
            ),
        },
        {
          title: 'p99 modelo × simulado',
          render: (_, row) => `${Math.round(row.modelP99Ms)} × ${Math.round(row.simP99Ms)} ms`,
        },
        {
          title: 'Erro do modelo',
          render: (_, row) => (
            <Space size={4}>
              <Tag color={row.modelReliable ? 'default' : 'error'}>
                {row.modelErrorPct.toFixed(0)}% ({(1 + row.modelErrorPct / 100).toFixed(1)}×)
              </Tag>
              {!row.modelReliable && <Tag color="error">não confiável</Tag>}
            </Space>
          ),
        },
      ]}
    />
  );
}

/**
 * When load > capacity, p50, p99 and the final backlog are a function of how long the run lasted
 * (240 s instead of 120 s roughly doubles them), not a property of the system. Show what is not.
 */
function SaturationPanel({ decision }: { decision: Decision }) {
  const saturated = decision.brokers.filter((b) => b.saturation);
  if (saturated.length === 0) return null;
  return (
    <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      <Alert
        type="error"
        showIcon
        message="Modo saturado: carga acima da capacidade"
        description="Nesse regime p50, p99 e backlog final crescem com a duração da simulação e não descrevem o sistema. No lugar, o que não depende dela."
      />
      {saturated.map((row) => {
        const s = row.saturation!;
        return (
          <Card key={row.broker} size="small" title={BROKER_LABEL[row.broker]} type="inner">
            <Descriptions size="small" column={{ xs: 1, md: 2 }}>
              <Descriptions.Item label="Déficit">
                {s.deficitPerSecond.toFixed(0)} msg/s ({s.ratePerSecond.toFixed(0)} chegando para{' '}
                {s.capacityPerSecond.toFixed(0)} de capacidade)
              </Descriptions.Item>
              <Descriptions.Item label="Tempo até o teto do broker">
                {ceilingText(s)}
              </Descriptions.Item>
              <Descriptions.Item label="Modo de falha">
                {failureModeLabel(s.failureMode)}
              </Descriptions.Item>
              <Descriptions.Item label="Custo acumulado (corrida)">
                {money(s.accumulatedCost)}
              </Descriptions.Item>
              <Descriptions.Item label="O que resolve" span={2}>
                {s.recommendation}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        );
      })}
    </Space>
  );
}
