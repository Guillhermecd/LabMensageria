import { CopyOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, List, Popconfirm, Space, Tag, Typography } from 'antd';
import type { Scenario } from '../../api/modules/types';

const BROKER_LABEL: Record<Scenario['broker'], string> = {
  KAFKA: 'Kafka',
  RABBITMQ: 'RabbitMQ',
  SQS: 'SQS',
};

type ScenarioListProps = {
  scenarios: Scenario[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onDuplicate: (id: string) => void;
  onDelete: (id: string) => void;
};

export function ScenarioList({
  scenarios,
  selectedId,
  onSelect,
  onCreate,
  onDuplicate,
  onDelete,
}: ScenarioListProps) {
  return (
    <div>
      <Button
        type="dashed"
        icon={<PlusOutlined />}
        onClick={onCreate}
        block
        style={{ marginBottom: 12 }}
      >
        Novo cenário
      </Button>
      <List
        dataSource={scenarios}
        renderItem={(scenario) => (
          <List.Item
            key={scenario.id}
            onClick={() => onSelect(scenario.id)}
            style={{
              cursor: 'pointer',
              padding: 12,
              borderRadius: 8,
              background: scenario.id === selectedId ? 'rgba(91,110,245,0.1)' : 'transparent',
            }}
            actions={[
              <Button
                key="duplicate"
                type="text"
                icon={<CopyOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  onDuplicate(scenario.id);
                }}
              />,
              <Popconfirm
                key="delete"
                title="Excluir cenário?"
                onConfirm={(e) => {
                  e?.stopPropagation();
                  onDelete(scenario.id);
                }}
              >
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={(e) => e.stopPropagation()}
                />
              </Popconfirm>,
            ]}
          >
            <List.Item.Meta
              title={scenario.name}
              description={
                <Space size={6}>
                  <Tag>{BROKER_LABEL[scenario.broker]}</Tag>
                  <Typography.Text type="secondary">
                    {scenario.ratePerSecond}/s · {scenario.consumers}c
                  </Typography.Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </div>
  );
}
