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
  retentionMb:
    'Kafka: tamanho do log retido. Quando o backlog × tamanho da mensagem passa disso, os registros mais antigos são apagados antes de serem consumidos (perda). Padrão em escala reduzida para a duração curta da simulação.',
  retentionHours:
    'Kafka: idade máxima de um registro no log (padrão 168 h = 7 dias). Mais antigo que isso é apagado.',
  highWatermarkMb:
    'RabbitMQ: quando a fila passa desse volume de memória, o broker bloqueia o produtor até o backlog cair. Nada é descartado, mas a produção é freada. Padrão em escala reduzida para a duração curta da simulação.',
  prefetch:
    'RabbitMQ: mensagens não confirmadas que cada consumidor pode reter. Com prefetch baixo o consumidor espera uma nova entrega a cada mensagem e perde capacidade.',
  inflightMax:
    'SQS: teto de mensagens em processamento ou invisíveis (aguardando retry) ao mesmo tempo. Ao esgotar, ninguém consegue receber novas mensagens (padrão 120.000).',
  dlqEnabled:
    'Dead-letter queue: destino das mensagens que esgotaram os retries. Evita que mensagens ruins (poison pill) circulem para sempre. Sem DLQ, a mensagem é descartada ao esgotar as tentativas.',
  burstEnabled:
    'Simula um burst: a produção triplica por um trecho. Serve para observar como o backlog cresce e quanto tempo leva para drenar.',
  serviceProfile:
    'Como o tempo de processamento varia em torno da média. Constante é o melhor caso possível (espera ~metade da exponencial); exponencial é o padrão realista; cauda longa simula 5% das mensagens levando 10× mais (GC, banco travando). A média é a mesma, mas a latência p99 muda bastante.',
  executionMode:
    'Simulado: motor matemático, instantâneo, qualquer broker. Real: publica de verdade em um RabbitMQ (único broker com adaptador real), com limites de taxa/duração para não sobrecarregar a máquina.',
} as const;
