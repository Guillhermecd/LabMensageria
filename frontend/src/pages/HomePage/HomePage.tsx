import { Button, Layout, Typography } from 'antd';
import { useAuth } from '../../hooks/useAuth';

export function HomePage() {
  const { logout } = useAuth();

  return (
    <Layout style={{ minHeight: '100vh', padding: 24 }}>
      <Typography.Title level={2}>Simulador de Mensageria</Typography.Title>
      <Typography.Paragraph>
        Fundação da Fase 0 pronta: autenticação, banco e estrutura de projeto. Os cenários e o motor
        de simulação chegam nas próximas fases.
      </Typography.Paragraph>
      <Button onClick={logout} style={{ width: 120 }}>
        Sair
      </Button>
    </Layout>
  );
}
