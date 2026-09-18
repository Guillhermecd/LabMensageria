import { Space, Typography } from 'antd';
import { BacklogChart } from '../ReportPage/BacklogChart';
import { ConsumerUtilization } from '../ReportPage/ConsumerUtilization';
import { KpiGrid } from '../ReportPage/KpiGrid';
import { LatencyChart } from '../ReportPage/LatencyChart';
import { MessageOutcomeBar } from '../ReportPage/MessageOutcomeBar';
import { ThroughputChart } from '../ReportPage/ThroughputChart';
import { buildKpis } from '../ReportPage/reportMetrics';
import type { RunReport } from '../../api/modules/types';

export function ComparisonColumn({ report }: { report: RunReport }) {
  const { scenario, run, ticks } = report;

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        {scenario.name}
      </Typography.Title>
      <KpiGrid kpis={buildKpis(scenario, run, ticks)} />
      <ThroughputChart ticks={ticks} />
      <BacklogChart scenario={scenario} ticks={ticks} />
      <LatencyChart ticks={ticks} />
      <ConsumerUtilization scenario={scenario} ticks={ticks} />
      <MessageOutcomeBar run={run} />
    </Space>
  );
}
