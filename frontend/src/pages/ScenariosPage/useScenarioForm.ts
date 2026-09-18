import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import type { Scenario, ScenarioFormValues } from '../../api/modules/types';
import { defaultScenario } from './defaultScenario';

export function useScenarioForm(
  scenario: Scenario | null,
  onSubmit: (values: ScenarioFormValues) => void,
) {
  const form = useForm<ScenarioFormValues>({
    defaultValues: scenario ?? defaultScenario,
  });

  useEffect(() => {
    form.reset(scenario ?? defaultScenario);
    // form.reset is stable across renders — only re-sync when the selected scenario changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenario]);

  const broker = form.watch('broker');
  const submit = form.handleSubmit(onSubmit);

  return { ...form, broker, submit };
}
