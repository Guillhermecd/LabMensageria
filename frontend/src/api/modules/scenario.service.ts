import { api } from './api';
import type { Scenario, ScenarioFormValues } from './types';

export const ScenarioService = {
  list() {
    return api<Scenario[]>('/scenarios');
  },
  get(id: string) {
    return api<Scenario>(`/scenarios/${id}`);
  },
  create(payload: ScenarioFormValues) {
    return api<Scenario>('/scenarios', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  update(id: string, payload: ScenarioFormValues) {
    return api<Scenario>(`/scenarios/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },
  remove(id: string) {
    return api<void>(`/scenarios/${id}`, { method: 'DELETE' });
  },
  duplicate(id: string) {
    return api<Scenario>(`/scenarios/${id}/duplicate`, { method: 'POST' });
  },
};
