// Textos copiados do protótipo (Simulador de Mensageria.dc.html) — mantêm a
// mesma explicação que o usuário já validou, não são redação nova.
export const scenarioFieldTips = {
  name: 'Identificação do cenário. Use nomes que descrevam a hipótese testada.',
  broker:
    'Tecnologia de mensageria simulada. Cada uma tem regras próprias de fila, retry, escala e cobrança.',
  ratePerSecond:
    'Quantas mensagens os produtores publicam por segundo. É a carga de entrada do sistema.',
  consumers:
    'Instâncias que leem e processam mensagens em paralelo. Mais consumidores = mais capacidade (até o limite do broker).',
  processingMs:
    'Tempo que um consumidor leva para tratar uma mensagem. Capacidade ≈ consumidores × 1000 ÷ ms.',
  failurePct:
    'Percentual de mensagens cujo processamento falha (erro, timeout) e precisa ser retentado.',
  maxRetries: 'Tentativas extras antes de a mensagem ser considerada perdida e ir para a DLQ.',
  messageSizeKb: 'Tamanho médio de cada mensagem. Afeta tráfego de rede, armazenamento e custo.',
  durationSeconds: 'Tempo total simulado, em segundos.',
  partitions:
    'Kafka divide o tópico em partições; cada partição é lida por um único consumidor do grupo. Define o paralelismo máximo.',
  queueCapacity:
    'Limite de mensagens na fila (max-length). Ao encher, as mais antigas são descartadas. 0 = sem limite.',
  visibilityTimeoutSeconds:
    'No SQS, após ser lida a mensagem fica invisível por esse tempo; se não for confirmada (delete), reaparece para retry.',
  dlqEnabled:
    'Dead-letter queue: destino das mensagens que esgotaram os retries. Evita que mensagens ruins (poison pill) circulem para sempre.',
  burstEnabled:
    'Simula um burst: a produção triplica por um trecho. Serve para observar como o backlog cresce e quanto tempo leva para drenar.',
  executionMode:
    'Simulado: motor matemático, instantâneo, qualquer broker. Real: publica de verdade em um RabbitMQ (único broker com adaptador real), com limites de taxa/duração para não sobrecarregar a máquina.',
} as const;
