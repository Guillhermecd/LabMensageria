import { ExperimentOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Space, Table, Typography } from 'antd';
import { useState } from 'react';
import { RunService } from '../../api/modules/run.service';
import type { BatchSummary, MetricRange } from '../../api/modules/types';
import { HelpLabel } from '../../components/ui/HelpLabel';

const ROUNDS = 30;

type Row = { key: string; label: string; unit: string; range: MetricRange; decimals: number };

function rows(batch: BatchSummary): Row[] {
  return [
    { key: 'p50', label: 'Latência p50', unit: 'ms', range: batch.p50Ms, decimals: 0 },
    { key: 'p95', label: 'Latência p95', unit: 'ms', range: batch.p95Ms, decimals: 0 },
    { key: 'p99', label: 'Latência p99', unit: 'ms', range: batch.p99Ms, decimals: 0 },
    { key: 'backlog', label: 'Pico de backlog', unit: 'msgs', range: batch.peakBacklog, decimals: 0 },
    { key: 'loss', label: 'Perda (DLQ + descartes)', unit: '%', range: batch.lossPct, decimals: 2 },
  ];
}

export function BatchPanel({
  scenarioId,
  disabled,
  onReplay,
}: {
  scenarioId: string;
  disabled: boolean;
  onReplay: (seed: number) => void;
}) {
  const [batch, setBatch] = useState<BatchSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setBatch(await RunService.runBatch(scenarioId, ROUNDS));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao rodar as rodadas');
    } finally {
      setLoading(false);
    }
  };

  const fmt = (value: number, decimals: number) => value.toFixed(decimals);

  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label={`Faixa de resultados (${ROUNDS} rodadas)`}
          tip="Uma execução é uma amostra sorteada. Aqui o mesmo cenário roda com várias sementes e mostramos a distribuição: mediana, p95 (5% das rodadas foram piores que isso) e a pior rodada. Dimensione pelo pior caso, não pela mediana. Estatísticas sem o aquecimento inicial."
        />
      }
      extra={
        <Button
          icon={<ExperimentOutlined />}
          onClick={run}
          loading={loading}
          disabled={disabled}
        >
          Rodar {ROUNDS} rodadas
        </Button>
      }
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      {!batch && !loading && !error && (
        <Typography.Text type="secondary">
          Rode várias rodadas para ver a faixa de resultados em vez de um único ponto.
        </Typography.Text>
      )}
      {batch && (
        <Space orientation="vertical" size={8} style={{ width: '100%' }}>
          <Table
            size="small"
            pagination={false}
            rowKey="key"
            dataSource={rows(batch)}
            columns={[
              { title: 'Métrica', dataIndex: 'label' },
              {
                title: 'Melhor',
                render: (_, r: Row) => `${fmt(r.range.min, r.decimals)} ${r.unit}`,
              },
              {
                title: 'Mediana',
                render: (_, r: Row) => `${fmt(r.range.median, r.decimals)} ${r.unit}`,
              },
              {
                title: 'p95',
                render: (_, r: Row) => `${fmt(r.range.p95, r.decimals)} ${r.unit}`,
              },
              {
                title: 'Pior rodada',
                render: (_, r: Row) => (
                  <Space size={4}>
                    <b>{`${fmt(r.range.worst, r.decimals)} ${r.unit}`}</b>
                    <Button size="small" type="link" onClick={() => onReplay(r.range.worstSeed)}>
                      reproduzir
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {batch.rounds} rodadas · semente-mestre {batch.masterSeed} · aquecimento de{' '}
            {batch.warmupSeconds}s descartado · capacidade bruta {Math.round(batch.rawCapacity)}/s,
            útil {Math.round(batch.usefulCapacity)}/s. “Reproduzir” abre a rodada exata (mesma
            semente) no relatório.
          </Typography.Text>
        </Space>
      )}
    </Card>
  );
}
