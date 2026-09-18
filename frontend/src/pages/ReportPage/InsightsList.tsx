import { Card, Empty, Space, Typography } from 'antd';
import { useTheme } from '../../hooks/useTheme';
import type { Insight } from '../../api/modules/types';

export function InsightsList({ insights }: { insights: Insight[] }) {
  const { mode, semantic } = useTheme();
  const palette = semantic[mode];

  return (
    <Card size="small" title="O que está acontecendo">
      {insights.length === 0 ? (
        <Empty description="Sem observações ainda." image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {insights.map((insight, i) => {
            const tone = palette[insight.tone];
            return (
              <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                <span
                  style={{
                    marginTop: 6,
                    width: 8,
                    height: 8,
                    borderRadius: 4,
                    background: tone.text,
                    flexShrink: 0,
                  }}
                />
                <Typography.Text style={{ fontSize: 13 }}>{insight.text}</Typography.Text>
              </div>
            );
          })}
        </Space>
      )}
    </Card>
  );
}
