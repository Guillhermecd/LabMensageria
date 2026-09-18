import { PlayCircleOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Empty, Row, Space, Spin, Typography } from 'antd';
import { BacklogChart } from './BacklogChart';
import { ConsumerUtilization } from './ConsumerUtilization';
import { EventTimeline } from './EventTimeline';
import { KpiGrid } from './KpiGrid';
import { LatencyChart } from './LatencyChart';
import { MessageOutcomeBar } from './MessageOutcomeBar';
import { ThroughputChart } from './ThroughputChart';
import { buildKpis } from './reportMetrics';
import { useRunReport } from './useRunReport';

export function ReportPanel({ scenarioId }: { scenarioId: string }) {
  const { scenario, run, ticks, events, running, error, runInstant } = useRunReport(scenarioId);
  const hasResult = !!(scenario && run && ticks.length > 0);

  return (
    <div>
      <Space
        align="center"
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          Relatório {scenario ? `— ${scenario.name}` : ''}
        </Typography.Title>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          onClick={runInstant}
          loading={running}
          disabled={!scenario}
        >
          Resultado instantâneo
        </Button>
      </Space>

      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} closable />}

      {running && <Spin description="Rodando simulação..." />}

      {!running && !hasResult && !error && (
        <Empty description="Clique em “Resultado instantâneo” para gerar o relatório deste cenário." />
      )}

      {hasResult && (
        <Space orientation="vertical" size={24} style={{ width: '100%' }}>
          <KpiGrid kpis={buildKpis(scenario, run, ticks)} />
          <Row gutter={[16, 16]}>
            <Col xs={24} xl={12}>
              <ThroughputChart ticks={ticks} />
            </Col>
            <Col xs={24} xl={12}>
              <BacklogChart scenario={scenario} ticks={ticks} />
            </Col>
          </Row>
          <Row gutter={[16, 16]}>
            <Col xs={24} xl={12}>
              <LatencyChart ticks={ticks} />
            </Col>
            <Col xs={24} xl={12}>
              <ConsumerUtilization scenario={scenario} ticks={ticks} />
            </Col>
          </Row>
          <Row gutter={[16, 16]}>
            <Col xs={24} xl={12}>
              <MessageOutcomeBar run={run} />
            </Col>
            <Col xs={24} xl={12}>
              <EventTimeline events={events} />
            </Col>
          </Row>
        </Space>
      )}
    </div>
  );
}
