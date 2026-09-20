import {
  DownloadOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Alert, Button, Col, Empty, Progress, Row, Space, Spin, Tag, Typography } from 'antd';
import { useRef } from 'react';
import { BacklogChart } from './BacklogChart';
import { ConclusionPanel } from './ConclusionPanel';
import { ConsumerUtilization } from './ConsumerUtilization';
import { BatchPanel } from './BatchPanel';
import { DecisionPanel } from './DecisionPanel';
import { SuitePanel } from './SuitePanel';
import { SweepPanel } from './SweepPanel';
import { EventTimeline } from './EventTimeline';
import { HelpLabel } from '../../components/ui/HelpLabel';
import { useTheme } from '../../hooks/useTheme';
import { InsightsList } from './InsightsList';
import { KpiGrid } from './KpiGrid';
import { LatencyChart } from './LatencyChart';
import { MessageOutcomeBar } from './MessageOutcomeBar';
import { NarrativePanel } from './NarrativePanel';
import { RunHistory } from './RunHistory';
import { ThroughputChart } from './ThroughputChart';
import { TradeoffCards } from './TradeoffCards';
import { buildKpis } from './reportMetrics';
import { useReportExport } from './useReportExport';
import { useRunReport } from './useRunReport';

export function ReportPanel({ scenarioId }: { scenarioId: string }) {
  const { config } = useTheme();
  const {
    scenario,
    tradeoffs,
    run,
    ticks,
    events,
    report,
    history,
    running,
    live,
    error,
    runInstant,
    runLive,
    stopLive,
    reopen,
  } = useRunReport(scenarioId);
  const { exportAsPng, exporting, exportedUrl, error: exportError } = useReportExport();
  const exportRef = useRef<HTMLDivElement>(null);

  const hasResult = !!(scenario && run && ticks.length > 0);
  const isReal = scenario?.executionMode === 'REAL';
  const isLiveRunning = running && live;
  const isInstantLoading = running && !live && ticks.length === 0;
  const lastSecond = ticks[ticks.length - 1]?.second ?? -1;
  const progressPct = scenario
    ? Math.min(100, Math.round(((lastSecond + 1) / scenario.durationSeconds) * 100))
    : 0;

  const handleExport = () => {
    if (!exportRef.current || !scenario) return;
    exportAsPng(
      exportRef.current,
      `relatorio-${scenario.name}`,
      config.token?.colorBgLayout ?? '#ffffff',
    );
  };

  return (
    <div>
      <Space
        align="center"
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}
      >
        <Space align="center">
          <Typography.Title level={4} style={{ margin: 0 }}>
            Relatório {scenario ? `— ${scenario.name}` : ''}
          </Typography.Title>
          {scenario && (
            <Tag color={isReal ? 'volcano' : 'blue'}>{isReal ? 'Real' : 'Simulado'}</Tag>
          )}
        </Space>
        <Space>
          {isLiveRunning ? (
            <Button danger icon={<PauseCircleOutlined />} onClick={stopLive}>
              Parar
            </Button>
          ) : (
            <>
              {hasResult && (
                <Button icon={<DownloadOutlined />} onClick={handleExport} loading={exporting}>
                  Exportar PNG
                </Button>
              )}
              <Button
                icon={<ThunderboltOutlined />}
                onClick={runLive}
                loading={running}
                disabled={!scenario}
              >
                {isReal ? 'Rodar (RabbitMQ real)' : 'Rodar ao vivo'}
              </Button>
              {!isReal && (
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={() => runInstant()}
                  loading={running}
                  disabled={!scenario}
                >
                  Resultado instantâneo
                </Button>
              )}
            </>
          )}
        </Space>
      </Space>

      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} closable />}
      {exportError && (
        <Alert type="error" message={exportError} style={{ marginBottom: 16 }} closable />
      )}
      {exportedUrl && (
        <Alert
          type="success"
          style={{ marginBottom: 16 }}
          closable
          message={
            <>
              Imagem exportada:{' '}
              <a href={exportedUrl} target="_blank" rel="noreferrer">
                abrir
              </a>
            </>
          }
        />
      )}

      {scenario && !isReal && (
        <div style={{ marginBottom: 16 }}>
          <BatchPanel
            scenarioId={scenarioId}
            disabled={running}
            onReplay={(seed) => runInstant(seed)}
          />
        </div>
      )}

      {scenario && !isReal && (
        <div style={{ marginBottom: 16 }}>
          <DecisionPanel scenarioId={scenarioId} disabled={running} />
        </div>
      )}

      {scenario && !isReal && (
        <div style={{ marginBottom: 16 }}>
          <SuitePanel scenarioId={scenarioId} disabled={running} />
        </div>
      )}

      {scenario && !isReal && (
        <div style={{ marginBottom: 16 }}>
          <SweepPanel scenarioId={scenarioId} disabled={running} />
        </div>
      )}

      {isLiveRunning && (
        <Progress percent={progressPct} status="active" style={{ marginBottom: 16 }} />
      )}

      {isInstantLoading && <Spin description="Rodando simulação..." />}

      {!running && !hasResult && !error && (
        <Empty description="Clique em “Resultado instantâneo” ou “Rodar ao vivo” para gerar o relatório deste cenário." />
      )}

      <div ref={exportRef} style={{ background: config.token?.colorBgLayout }}>
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
                  <LatencyChart scenario={scenario} ticks={ticks} />
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

      {history.length > 0 && (
        <div style={{ marginTop: 24 }}>
          <RunHistory history={history} activeRunId={run?.id ?? null} onReopen={reopen} />
        </div>
      )}
    </div>
  );
}
