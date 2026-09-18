import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { AuthService } from '../../api/modules/auth.service';
import { useAuth } from '../../hooks/useAuth';

type LoginFormValues = {
  email: string;
  password: string;
};

export function useLoginForm() {
  const { control, handleSubmit, formState } = useForm<LoginFormValues>({
    defaultValues: { email: '', password: '' },
  });
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    setLoading(true);
    try {
      const response = await AuthService.login(values);
      login(response.token, response.user);
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao entrar');
    } finally {
      setLoading(false);
    }
  });

  return { control, onSubmit, error, loading, errors: formState.errors };
}
