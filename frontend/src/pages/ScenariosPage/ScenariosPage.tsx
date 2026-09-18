import { Alert, Space, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ScenarioService } from '../../api/modules/scenario.service';
import { useTheme } from '../../hooks/useTheme';
import type { Scenario, ScenarioFormValues } from '../../api/modules/types';
import { ReportPanel } from '../ReportPage';
import { ScenarioForm } from './ScenarioForm';
import { ScenarioList } from './ScenarioList';

export function ScenariosPage() {
  const { config } = useTheme();
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
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '340px minmax(0,1fr)',
        minHeight: '100vh',
        background: config.token?.colorBgLayout,
        color: config.token?.colorText,
      }}
    >
      <aside
        style={{
          borderRight: '1px solid rgba(128,128,128,0.2)',
          background: config.token?.colorBgBase,
          padding: 20,
          overflowY: 'auto',
          height: '100vh',
          position: 'sticky',
          top: 0,
        }}
      >
        <Typography.Title level={4}>Cenários</Typography.Title>
        {error && (
          <Alert
            type="error"
            message={error}
            style={{ marginBottom: 16 }}
            closable
            onClose={() => setError(null)}
          />
        )}
        <Space orientation="vertical" style={{ width: '100%' }} size={16}>
          <ScenarioList
            scenarios={scenarios}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onCreate={handleCreate}
            onDuplicate={handleDuplicate}
            onDelete={handleDelete}
          />
          <ScenarioForm scenario={selected} saving={saving} onSubmit={handleSubmit} />
        </Space>
      </aside>

      <main style={{ padding: 24 }}>
        {selected ? (
          <ReportPanel key={selected.id} scenarioId={selected.id} />
        ) : (
          <Typography.Text type="secondary">
            Crie ou selecione um cenário para ver o relatório.
          </Typography.Text>
        )}
      </main>
    </div>
  );
}
