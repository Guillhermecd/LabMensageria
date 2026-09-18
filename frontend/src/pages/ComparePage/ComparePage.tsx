import { ArrowLeftOutlined, SwapOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Empty, Row, Select, Space, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useTheme } from '../../hooks/useTheme';
import { ComparisonColumn } from './ComparisonColumn';
import { useCompare } from './useCompare';

export function ComparePage() {
  const { config } = useTheme();
  const {
    scenarios,
    scenarioIdA,
    scenarioIdB,
    setScenarioIdA,
    setScenarioIdB,
    result,
    loading,
    error,
    compare,
  } = useCompare();

  const options = scenarios.map((s) => ({ value: s.id, label: s.name }));

  return (
    <div
      style={{
        padding: 24,
        minHeight: '100vh',
        background: config.token?.colorBgLayout,
        color: config.token?.colorText,
      }}
    >
      <Space align="center" style={{ marginBottom: 16 }}>
        <Link to="/">
          <Button icon={<ArrowLeftOutlined />} type="text">
            Cenários
          </Button>
        </Link>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Comparar cenários
        </Typography.Title>
      </Space>

      <Card size="small" style={{ marginBottom: 24 }}>
        <Space wrap align="center">
          <Select
            style={{ width: 240 }}
            placeholder="Cenário A"
            value={scenarioIdA}
            onChange={setScenarioIdA}
            options={options}
          />
          <Typography.Text type="secondary">vs</Typography.Text>
          <Select
            style={{ width: 240 }}
            placeholder="Cenário B"
            value={scenarioIdB}
            onChange={setScenarioIdB}
            options={options}
          />
          <Button
            type="primary"
            icon={<SwapOutlined />}
            onClick={compare}
            loading={loading}
            disabled={!scenarioIdA || !scenarioIdB || scenarioIdA === scenarioIdB}
          >
            Comparar
          </Button>
        </Space>
        {scenarioIdA && scenarioIdA === scenarioIdB && (
          <Typography.Text type="warning" style={{ display: 'block', marginTop: 8 }}>
            Escolha dois cenários diferentes.
          </Typography.Text>
        )}
      </Card>

      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} closable />}

      {loading && <Spin description="Rodando as duas simulações..." />}

      {!loading && !result && !error && (
        <Empty description="Escolha dois cenários e clique em “Comparar”." />
      )}

      {result && (
        <Space orientation="vertical" size={24} style={{ width: '100%' }}>
          <Card size="small">
            <Typography.Paragraph style={{ margin: 0, fontSize: 13, lineHeight: 1.7 }}>
              {result.comparison}
            </Typography.Paragraph>
          </Card>
          <Row gutter={[24, 24]}>
            <Col xs={24} lg={12}>
              <ComparisonColumn report={result.a} />
            </Col>
            <Col xs={24} lg={12}>
              <ComparisonColumn report={result.b} />
            </Col>
          </Row>
        </Space>
      )}
    </div>
  );
}
