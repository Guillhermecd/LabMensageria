import { Col, Row } from 'antd';
import { StatCard } from '../../components/ui/StatCard';
import type { Kpi } from './reportMetrics';

export function KpiGrid({ kpis }: { kpis: Kpi[] }) {
  return (
    <Row gutter={[16, 16]}>
      {kpis.map((kpi) => (
        <Col key={kpi.label} xs={24} sm={12} md={8} lg={6}>
          <StatCard
            label={kpi.label}
            value={kpi.value}
            sub={kpi.sub}
            tone={kpi.tone}
            tip={kpi.tip}
          />
        </Col>
      ))}
    </Row>
  );
}
