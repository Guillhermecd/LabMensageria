import { Button, Form, Input, InputNumber, Select, Switch } from 'antd';
import { Controller, type Control } from 'react-hook-form';
import { HelpLabel } from '../../components/ui/HelpLabel';
import type { Scenario, ScenarioFormValues } from '../../api/modules/types';
import { scenarioFieldTips } from './scenarioFieldTips';
import { useScenarioForm } from './useScenarioForm';

const BROKER_OPTIONS = [
  { value: 'KAFKA', label: 'Kafka' },
  { value: 'RABBITMQ', label: 'RabbitMQ' },
  { value: 'SQS', label: 'SQS / SNS' },
];

const SERVICE_PROFILE_OPTIONS = [
  { value: 'CONSTANT', label: 'Constante (melhor caso)' },
  { value: 'EXPONENTIAL', label: 'Exponencial (realista)' },
  { value: 'HEAVY_TAIL', label: 'Cauda longa (5% a 10×)' },
];

type ScenarioFormProps = {
  scenario: Scenario | null;
  saving: boolean;
  onSubmit: (values: ScenarioFormValues) => void;
};

export function ScenarioForm({ scenario, saving, onSubmit }: ScenarioFormProps) {
  const { control, submit, broker } = useScenarioForm(scenario, onSubmit);

  return (
    <form onSubmit={submit}>
      <Form.Item label={<HelpLabel label="Nome" tip={scenarioFieldTips.name} />}>
        <Controller name="name" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>

      <Form.Item label={<HelpLabel label="Broker" tip={scenarioFieldTips.broker} />}>
        <Controller
          name="broker"
          control={control}
          render={({ field }) => (
            <Select {...field} options={BROKER_OPTIONS} style={{ width: '100%' }} />
          )}
        />
      </Form.Item>

      <NumberField
        control={control}
        name="ratePerSecond"
        label="Taxa (msg/s)"
        tip={scenarioFieldTips.ratePerSecond}
      />
      <NumberField
        control={control}
        name="consumers"
        label="Consumidores"
        tip={scenarioFieldTips.consumers}
      />
      <NumberField
        control={control}
        name="processingMs"
        label="Processamento (ms)"
        tip={scenarioFieldTips.processingMs}
      />
      <Form.Item label={<HelpLabel label="Variação do serviço" tip={scenarioFieldTips.serviceProfile} />}>
        <Controller
          name="serviceProfile"
          control={control}
          render={({ field }) => (
            <Select {...field} options={SERVICE_PROFILE_OPTIONS} style={{ width: '100%' }} />
          )}
        />
      </Form.Item>
      <NumberField
        control={control}
        name="failurePct"
        label="Falha (%)"
        tip={scenarioFieldTips.failurePct}
        min={0}
        max={100}
      />
      <NumberField
        control={control}
        name="maxRetries"
        label="Tentativas máximas"
        tip={scenarioFieldTips.maxRetries}
      />
      <NumberField
        control={control}
        name="messageSizeKb"
        label="Tamanho da mensagem (KB)"
        tip={scenarioFieldTips.messageSizeKb}
      />
      <NumberField
        control={control}
        name="durationSeconds"
        label="Duração (s)"
        tip={scenarioFieldTips.durationSeconds}
      />

      {broker === 'KAFKA' && (
        <>
          <NumberField
            control={control}
            name="partitions"
            label="Partições"
            tip={scenarioFieldTips.partitions}
          />
          <NumberField
            control={control}
            name="retentionMb"
            label="Retenção por tamanho (MB)"
            tip={scenarioFieldTips.retentionMb}
            min={1}
            placeholder="256"
          />
          <NumberField
            control={control}
            name="retentionHours"
            label="Retenção por tempo (h)"
            tip={scenarioFieldTips.retentionHours}
            min={1}
            placeholder="168"
          />
        </>
      )}
      {broker === 'RABBITMQ' && (
        <>
          <NumberField
            control={control}
            name="highWatermarkMb"
            label="High watermark (MB)"
            tip={scenarioFieldTips.highWatermarkMb}
            min={1}
            placeholder="256"
          />
          <NumberField
            control={control}
            name="prefetch"
            label="Prefetch"
            tip={scenarioFieldTips.prefetch}
            min={1}
            placeholder="250"
          />
          <NumberField
            control={control}
            name="queueCapacity"
            label="Capacidade da fila (max-length)"
            tip={scenarioFieldTips.queueCapacity}
            min={0}
          />
        </>
      )}
      {broker === 'SQS' && (
        <>
          <NumberField
            control={control}
            name="visibilityTimeoutSeconds"
            label="Visibility timeout (s)"
            tip={scenarioFieldTips.visibilityTimeoutSeconds}
            min={1}
          />
          <NumberField
            control={control}
            name="inflightMax"
            label="In-flight máximo"
            tip={scenarioFieldTips.inflightMax}
            min={1}
            placeholder="120000"
          />
        </>
      )}

      <Form.Item label={<HelpLabel label="DLQ habilitada" tip={scenarioFieldTips.dlqEnabled} />}>
        <Controller
          name="dlqEnabled"
          control={control}
          render={({ field: { value, onChange } }) => (
            <Switch checked={value} onChange={onChange} />
          )}
        />
      </Form.Item>

      <Form.Item label={<HelpLabel label="Simular burst" tip={scenarioFieldTips.burstEnabled} />}>
        <Controller
          name="burstEnabled"
          control={control}
          render={({ field: { value, onChange } }) => (
            <Switch checked={value} onChange={onChange} />
          )}
        />
      </Form.Item>

      <Form.Item label={<HelpLabel label="Execução" tip={scenarioFieldTips.executionMode} />}>
        <Controller
          name="executionMode"
          control={control}
          render={({ field: { value, onChange } }) => (
            <Switch
              checked={value === 'REAL'}
              disabled={broker !== 'RABBITMQ'}
              checkedChildren="Real"
              unCheckedChildren="Simulado"
              onChange={(checked) => onChange(checked ? 'REAL' : 'SIMULATED')}
            />
          )}
        />
        {broker !== 'RABBITMQ' && (
          <div style={{ marginTop: 4, fontSize: 12, opacity: 0.65 }}>
            Execução real só está disponível para RabbitMQ.
          </div>
        )}
      </Form.Item>

      <Button type="primary" htmlType="submit" loading={saving} block>
        Salvar cenário
      </Button>
    </form>
  );
}

type NumberFieldProps = {
  control: Control<ScenarioFormValues>;
  name: keyof ScenarioFormValues;
  label: string;
  tip: string;
  min?: number;
  max?: number;
  placeholder?: string;
};

function NumberField({ control, name, label, tip, min, max, placeholder }: NumberFieldProps) {
  return (
    <Form.Item label={<HelpLabel label={label} tip={tip} />}>
      <Controller
        name={name}
        control={control}
        render={({ field }) => (
          <InputNumber
            {...field}
            value={field.value as number | null}
            min={min}
            max={max}
            placeholder={placeholder}
            style={{ width: '100%' }}
          />
        )}
      />
    </Form.Item>
  );
}
