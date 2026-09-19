import { CheckCircleFilled } from '@ant-design/icons';
import { Card, Col, Row, Space, Typography } from 'antd';
import { useTheme } from '../../hooks/useTheme';
import type { Broker, Tradeoff } from '../../api/modules/types';
import { formatCount } from '../../lib/format';

const BROKER_LABEL: Record<Broker, string> = {
  KAFKA: 'Kafka',
  RABBITMQ: 'RabbitMQ',
  SQS: 'SQS/SNS',
};

export function TradeoffCards({ tradeoffs }: { tradeoffs: Tradeoff[] }) {
  const { mode, semantic } = useTheme();
  const palette = semantic[mode];

  return (
    <Row gutter={[16, 16]}>
      {tradeoffs.map((tradeoff) => {
        const ratioTone =
          tradeoff.ratio > 1 ? palette.danger : tradeoff.ratio > 0.8 ? palette.warning : palette.ok;
        const lossTone = tradeoff.lossPct > 0 ? palette.danger : palette.ok;
        return (
          <Col key={tradeoff.broker} xs={24} md={8}>
            <Card
              size="small"
              style={tradeoff.best ? { borderColor: palette.ok.text, borderWidth: 2 } : undefined}
              title={
                <Space>
                  {tradeoff.best && <CheckCircleFilled style={{ color: palette.ok.text }} />}
                  {BROKER_LABEL[tradeoff.broker]}
                </Space>
              }
              extra={<Typography.Text strong>{tradeoff.score} pts</Typography.Text>}
            >
              <Space direction="vertical" size={4} style={{ width: '100%', fontSize: 12 }}>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Capacidade</Typography.Text>
                  <Typography.Text>{Math.round(tradeoff.capacity)}/s</Typography.Text>
                </Row>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Carga × capacidade</Typography.Text>
                  <Typography.Text style={{ color: ratioTone.text }}>
                    {tradeoff.ratio.toFixed(2)}×
                  </Typography.Text>
                </Row>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Latência p50</Typography.Text>
                  <Typography.Text>{Math.round(tradeoff.p50Ms)} ms</Typography.Text>
                </Row>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Cauda p99</Typography.Text>
                  <Typography.Text>{Math.round(tradeoff.p99Ms)} ms</Typography.Text>
                </Row>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Backlog estimado</Typography.Text>
                  <Typography.Text>{formatCount(tradeoff.backlog)} msgs</Typography.Text>
                </Row>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Perda estimada</Typography.Text>
                  <Typography.Text style={{ color: lossTone.text }}>
                    {formatCount(tradeoff.loss)} ({tradeoff.lossPct.toFixed(2)}%)
                  </Typography.Text>
                </Row>
                <Row justify="space-between">
                  <Typography.Text type="secondary">Custo estimado</Typography.Text>
                  <Typography.Text>${tradeoff.cost.toFixed(3)}</Typography.Text>
                </Row>
              </Space>

              <div style={{ marginTop: 12 }}>
                {tradeoff.pros.map((pro, i) => (
                  <Typography.Text
                    key={`pro-${i}`}
                    style={{ display: 'flex', gap: 6, fontSize: 12, color: palette.ok.text }}
                  >
                    <span>+</span>
                    <span>{pro}</span>
                  </Typography.Text>
                ))}
                {tradeoff.cons.map((con, i) => (
                  <Typography.Text
                    key={`con-${i}`}
                    style={{ display: 'flex', gap: 6, fontSize: 12, color: palette.danger.text }}
                  >
                    <span>−</span>
                    <span>{con}</span>
                  </Typography.Text>
                ))}
              </div>
            </Card>
          </Col>
        );
      })}
    </Row>
  );
}
