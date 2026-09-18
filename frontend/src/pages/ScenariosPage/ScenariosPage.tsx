import { Alert, Col, Layout, Row, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ScenarioService } from '../../api/modules/scenario.service';
import type { Scenario, ScenarioFormValues } from '../../api/modules/types';
import { ScenarioForm } from './ScenarioForm';
import { ScenarioList } from './ScenarioList';

export function ScenariosPage() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async (selectAfter?: string) => {
    const list = await ScenarioService.list();
    setScenarios(list);
    setSelectedId((current) => selectAfter ?? current ?? list[0]?.id ?? null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    ScenarioService.list()
      .then((list) => {
        if (cancelled) return;
        setScenarios(list);
        setSelectedId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Falha ao carregar cenários');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const selected = scenarios.find((s) => s.id === selectedId) ?? null;

  const handleSubmit = async (values: ScenarioFormValues) => {
    setError(null);
    setSaving(true);
    try {
      if (selected) {
        await ScenarioService.update(selected.id, values);
        await refresh(selected.id);
      } else {
        const created = await ScenarioService.create(values);
        await refresh(created.id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao salvar cenário');
    } finally {
      setSaving(false);
    }
  };

  const handleCreate = () => setSelectedId(null);

  const handleDuplicate = async (id: string) => {
    const copy = await ScenarioService.duplicate(id);
    await refresh(copy.id);
  };

  const handleDelete = async (id: string) => {
    await ScenarioService.remove(id);
    if (id === selectedId) setSelectedId(null);
    await refresh();
  };

  return (
    <Layout style={{ minHeight: '100vh', padding: 24 }}>
      <Typography.Title level={3}>Cenários</Typography.Title>
      {error && (
        <Alert
          type="error"
          message={error}
          style={{ marginBottom: 16 }}
          closable
          onClose={() => setError(null)}
        />
      )}
      <Row gutter={24}>
        <Col span={8}>
          <ScenarioList
            scenarios={scenarios}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onCreate={handleCreate}
            onDuplicate={handleDuplicate}
            onDelete={handleDelete}
          />
        </Col>
        <Col span={16}>
          <ScenarioForm scenario={selected} saving={saving} onSubmit={handleSubmit} />
        </Col>
      </Row>
    </Layout>
  );
}
