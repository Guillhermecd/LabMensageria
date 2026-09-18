import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { Controller } from 'react-hook-form';
import { BRAND_GRADIENT } from '../../theme';
import { useLoginForm } from './useLoginForm';

export function LoginPage() {
  const { control, onSubmit, error, loading, errors } = useLoginForm();

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: BRAND_GRADIENT,
      }}
    >
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>
          Simulador de Mensageria
        </Typography.Title>
        <form onSubmit={onSubmit}>
          <Form.Item
            label="Email"
            validateStatus={errors.email ? 'error' : ''}
            help={errors.email?.message}
          >
            <Controller
              name="email"
              control={control}
              rules={{ required: 'Informe o email' }}
              render={({ field }) => <Input {...field} type="email" autoComplete="email" />}
            />
          </Form.Item>
          <Form.Item
            label="Senha"
            validateStatus={errors.password ? 'error' : ''}
            help={errors.password?.message}
          >
            <Controller
              name="password"
              control={control}
              rules={{ required: 'Informe a senha' }}
              render={({ field }) => <Input.Password {...field} autoComplete="current-password" />}
            />
          </Form.Item>
          {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
          <Button type="primary" htmlType="submit" loading={loading} block>
            Entrar
          </Button>
        </form>
      </Card>
    </div>
  );
}
