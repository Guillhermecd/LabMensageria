import { Card, Tooltip, Typography } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { useTheme } from '../../hooks/useTheme';
import type { SemanticTone } from '../../theme';

type StatCardProps = {
  label: string;
  value: string;
  sub?: string;
  tone?: SemanticTone;
  tip?: string;
};

export function StatCard({ label, value, sub, tone, tip }: StatCardProps) {
  const { mode, semantic } = useTheme();
  const palette = semantic[mode];
  const color = tone ? palette[tone].text : undefined;

  return (
    <Card size="small" style={{ height: '100%' }}>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {label}
        {tip && (
          <Tooltip title={tip}>
            <QuestionCircleOutlined style={{ marginLeft: 4, color: '#8B939E', cursor: 'help' }} />
          </Tooltip>
        )}
      </Typography.Text>
      <div style={{ fontSize: 22, fontWeight: 600, color: color ?? 'inherit', lineHeight: 1.4 }}>
        {value}
      </div>
      {sub && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {sub}
        </Typography.Text>
      )}
    </Card>
  );
}
