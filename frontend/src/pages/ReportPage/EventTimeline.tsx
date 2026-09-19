import { Card, Empty, Tag, Timeline, Typography } from 'antd';
import { useTheme } from '../../hooks/useTheme';
import type { EventType, SimulationEvent } from '../../api/modules/types';
import type { SemanticTone } from '../../theme';

const EVENT_TONE: Record<EventType, SemanticTone> = {
  QUEUE_FULL: 'danger',
  FIRST_DLQ: 'danger',
  SATURATION: 'danger',
  LAG: 'warning',
  BURST_START: 'info',
  BURST_END: 'info',
  RECOVERED: 'ok',
  FINISHED: 'info',
};

export function EventTimeline({ events }: { events: SimulationEvent[] }) {
  const { mode, semantic } = useTheme();
  const palette = semantic[mode];

  return (
    <Card
      size="small"
      title="Linha do tempo"
      styles={{ body: { maxHeight: 320, overflowY: 'auto' } }}
    >
      {events.length === 0 ? (
        <Empty description="Sem eventos" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Timeline
          items={[...events].reverse().map((event) => {
            const tone = palette[EVENT_TONE[event.type]];
            return {
              color: tone.text,
              content: (
                <>
                  <Tag color={tone.bg} style={{ color: tone.text, borderColor: tone.text }}>
                    {event.second}s
                  </Tag>
                  <Typography.Text>{event.message}</Typography.Text>
                </>
              ),
            };
          })}
        />
      )}
    </Card>
  );
}
