import { QuestionCircleOutlined } from '@ant-design/icons';
import { Space, Tooltip } from 'antd';

type HelpLabelProps = {
  label: string;
  tip: string;
};

export function HelpLabel({ label, tip }: HelpLabelProps) {
  return (
    <Space size={4}>
      <span>{label}</span>
      <Tooltip title={tip}>
        <QuestionCircleOutlined style={{ color: '#8B939E', cursor: 'help' }} />
      </Tooltip>
    </Space>
  );
}
