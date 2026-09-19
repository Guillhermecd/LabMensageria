import { Card, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';

export function ConclusionPanel({ conclusion }: { conclusion: string[] }) {
  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Conclusão"
          tip="Texto final que junta o modelo analítico com o que a simulação de fato registrou."
        />
      }
    >
      {conclusion.map((paragraph, i) => (
        <Typography.Paragraph key={i} style={{ margin: '0 0 8px', fontSize: 13, lineHeight: 1.7 }}>
          {paragraph}
        </Typography.Paragraph>
      ))}
    </Card>
  );
}
