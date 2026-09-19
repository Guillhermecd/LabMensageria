import { Card, Typography } from 'antd';
import { HelpLabel } from '../../components/ui/HelpLabel';

export function NarrativePanel({ narrative }: { narrative: string }) {
  return (
    <Card
      size="small"
      title={
        <HelpLabel
          label="Narrativa"
          tip="Descrição gerada a partir dos ticks da simulação: fases de carga, quando o backlog subiu, quando drenou e como terminou."
        />
      }
    >
      <Typography.Paragraph style={{ margin: 0, fontSize: 13, lineHeight: 1.7 }}>
        {narrative}
      </Typography.Paragraph>
    </Card>
  );
}
