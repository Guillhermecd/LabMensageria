# Glossário

Termos de mensageria usados nos docs e no código deste projeto.

**Backlog** — mensagens que já foram produzidas mas ainda não foram processadas; a fila "esperando". Cresce quando a produção supera a capacidade de consumo. No Kafka é comumente chamado de **lag**.

**Lag** — nome que o Kafka dá ao backlog: a diferença entre o offset mais recente produzido numa partição e o offset que o consumidor já processou.

**DLQ (Dead-Letter Queue)** — destino de mensagens que esgotaram as tentativas de reprocessamento (retries). Existe para não deixar uma mensagem com erro ("poison pill") circulando indefinidamente e consumindo capacidade dos consumidores.

**Visibility timeout** — no SQS, tempo que uma mensagem lida fica invisível para outros consumidores. Se não for confirmada (deletada) dentro desse tempo, volta a aparecer na fila para uma nova tentativa.

**Partição** — no Kafka, cada tópico é dividido em partições; cada partição só é lida por um consumidor de cada grupo por vez. Define o paralelismo máximo: consumidores além do número de partições ficam ociosos.

**Max-length / overflow** — no RabbitMQ, limite opcional de mensagens numa fila. Ao encher, a política padrão (`drop-head`) descarta as mensagens mais antigas.

**M/M/c** — notação de teoria de filas: chegadas aleatórias (Poisson, o primeiro M), tempo de processamento aleatório (exponencial, o segundo M), `c` servidores/consumidores em paralelo. Ver `docs/05-tradeoff-report.md` e ADR 0005 para a versão simplificada usada aqui.

**p50 / p95 / p99** — percentis de latência. p50 é a mediana (metade das mensagens é mais rápida que isso); p95/p99 são a cauda — os 5%/1% mais lentos, onde aparecem os efeitos de fila cheia e retries. Usados em vez da média porque a média esconde a cauda lenta.

**Backpressure** — quando um sistema reduz ou pausa a entrada de novos dados porque não consegue processar no ritmo em que chegam. Neste simulador se manifesta como backlog crescente quando `ratio = carga / capacidade > 1`.

**SSE (Server-Sent Events)** — protocolo HTTP unidirecional (servidor → cliente) usado pela Fase 4 para transmitir os ticks da simulação ao vivo. Ver ADR 0002.

**Retry** — nova tentativa de processar uma mensagem que falhou. Cada broker modela isso de forma diferente: Kafka/RabbitMQ retentam no próximo ciclo, SQS espera o visibility timeout.
