import { InputNumber, Space, Typography } from 'antd';

type SeedControlProps = {
  value: number | null;
  onChange: (value: number | null) => void;
  lastSeed?: number;
};

/** The master seed is always visible and can be typed back in to re-run the exact same rounds. */
export function SeedControl({ value, onChange, lastSeed }: SeedControlProps) {
  return (
    <Space size={8} wrap>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Semente-mestre
      </Typography.Text>
      <InputNumber
        size="small"
        min={0}
        max={Number.MAX_SAFE_INTEGER}
        controls={false}
        value={value}
        placeholder={lastSeed != null ? String(lastSeed) : 'aleatória'}
        onChange={(v) => onChange(v == null ? null : Math.trunc(v))}
        style={{ width: 170 }}
      />
      {lastSeed != null && value !== lastSeed && (
        <Typography.Link style={{ fontSize: 12 }} onClick={() => onChange(lastSeed)}>
          usar a da última execução ({lastSeed})
        </Typography.Link>
      )}
    </Space>
  );
}
