import { PauseCircleOutlined, PlayCircleOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Empty, Progress, Row, Space, Spin, Typography } from 'antd';
import { BacklogChart } from './BacklogChart';
import { ConclusionPanel } from './ConclusionPanel';
import { ConsumerUtilization } from './ConsumerUtilization';
import { EventTimeline } from './EventTimeline';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { InsightsList } from './InsightsList';
import { KpiGrid } from './KpiGrid';
import { LatencyChart } from './LatencyChart';
import { MessageOutcomeBar } from './MessageOutcomeBar';
import { NarrativePanel } from './NarrativePanel';
import { ThroughputChart } from './ThroughputChart';
import { TradeoffCards } from './TradeoffCards';
import { buildKpis } from './reportMetrics';
import { useRunReport } from './useRunReport';

export function ReportPanel({ scenarioId }: { scenarioId: string }) {
  const {
    scenario,
    tradeoffs,
    run,
    ticks,
    events,
    report,
    running,
    live,
    error,
    runInstant,
    runLive,
    stopLive,
  } = useRunReport(scenarioId);

  const hasResult = !!(scenario && run && ticks.length > 0);
  const isLiveRunning = running && live;
  const isInstantLoading = running && !live && ticks.length === 0;
  const lastSecond = ticks[ticks.length - 1]?.second ?? -1;
  const progressPct = scenario
    ? Math.min(100, Math.round(((lastSecond + 1) / scenario.durationSeconds) * 100))
    : 0;

  return (
    <div>
      <Space
        align="center"
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          Relatório {scenario ? `— ${scenario.name}` : ''}
        </Typography.Title>
        <Space>
          {isLiveRunning ? (
            <Button danger icon={<PauseCircleOutlined />} onClick={stopLive}>
              Parar
            </Button>
          ) : (
            <>
              <Button
                icon={<ThunderboltOutlined />}
                onClick={runLive}
                loading={running}
                disabled={!scenario}
              >
                Rodar ao vivo
              </Button>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={runInstant}
                loading={running}
                disabled={!scenario}
              >
                Resultado instantâneo
              </Button>
            </>
          )}
        </Space>
      </Space>

      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} closable />}

      {isLiveRunning && (
        <Progress percent={progressPct} status="active" style={{ marginBottom: 16 }} />
      )}

      {isInstantLoading && <Spin description="Rodando simulação..." />}

      {!running && !hasResult && !error && (
        <Empty description="Clique em “Resultado instantâneo” ou “Rodar ao vivo” para gerar o relatório deste cenário." />
      )}

      <Space orientation="vertical" size={24} style={{ width: '100%' }}>
        {tradeoffs.length > 0 && (
          <div>
            <Typography.Title level={5} style={{ marginBottom: 12 }}>
              <HelpLabel
                label="Kafka × RabbitMQ × SQS"
                tip="Modelo analítico (teoria de filas M/M/c simplificada) aplicado aos parâmetros do cenário selecionado em cada broker. Não depende de rodar a simulação."
              />
            </Typography.Title>
            <TradeoffCards tradeoffs={tradeoffs} />
          </div>
        )}

        {hasResult && (
          <>
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
          </>
        )}

        {report && (
          <>
            <Row gutter={[16, 16]}>
              <Col xs={24} xl={12}>
                <InsightsList insights={report.insights} />
              </Col>
              <Col xs={24} xl={12}>
                <NarrativePanel narrative={report.narrative} />
              </Col>
            </Row>
            <ConclusionPanel conclusion={report.conclusion} />
          </>
        )}
      </Space>
    </div>
  );
}
