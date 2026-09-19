# 08 — Execução real (RabbitMQ)

## 1. Objetivo

Sair do modelo matemático e rodar um cenário de verdade contra um broker real, para comparar o que a simulação previu com o que aconteceu de fato. Escopo original do `PLAN.md` previa Kafka + RabbitMQ + SQS reais (6 dias, Testcontainers); reduzido para um único broker — RabbitMQ, o único que roda localmente com um `docker run` e sem infraestrutura extra (Zookeeper/KRaft para Kafka, conta AWS para SQS).

## 2. Conceito de mensageria estudado

A diferença entre um **modelo analítico** e a **coisa de verdade**. O motor simulado (Fase 2) e o modelo M/M/c (Fase 5) descrevem RabbitMQ com fórmulas; esta fase mede um RabbitMQ real com a mesma carga e compara. Também é a primeira vez que o projeto lida com concorrência real (múltiplos consumidores lendo a mesma fila ao mesmo tempo, de verdade, não simulados por um `Random`) e com um ciclo de vida real de fila (declarar, publicar, consumir, purgar, deletar).

## 3. Decisões de design

- **Ports and adapters, não um `if (real)` no motor simulado.** `MessageBrokerPort` é a porta; `RabbitMqBrokerAdapter` é o único adaptador hoje. `RealSimulationService` é um orquestrador paralelo a `LiveSimulationService`, nunca chama `SimulationEngine.step()`. Ver [ADR 0006](adr/0006-ports-and-adapters-for-brokers.md) — a divergência entre simulado e real é o objeto de estudo, então o caminho real não pode "vazar" para dentro do motor simulado nem o contrário.
- **Falha/retry/DLQ continuam sendo decisão do simulador, não do RabbitMQ.** `RealSimulationService.processMessage` decide `OK`/`RETRY`/`DLQ`/`DROP` usando `failurePct`/`maxRetries`/`dlqEnabled` do cenário — a mesma regra do motor simulado, só que aplicada a uma mensagem de verdade. O que é real é o transporte (rede, fila, concorrência dos consumidores) e a medição (profundidade de fila, latência); o que "dá errado" continua sendo simulado, porque um RabbitMQ local saudável não tem 10% de taxa de erro por conta própria.
- **Retry via header próprio, não `x-death` nativo.** `RabbitMqBrokerAdapter` usa um header `x-msgsim-retry-count` que ele mesmo escreve e incrementa a cada republicação — mais simples de ler no código do que o formato de array do `x-death` do RabbitMQ, ao custo de não ser "o jeito nativo" de rastrear retries. Aceitável porque a fila é efêmera e de uso interno; não há integração externa que espere o formato nativo.
- **Tetos de segurança (`ratePerSecond <= 500`, `durationSeconds <= 120`) só para `executionMode=REAL`.** Um cenário simulado com `ratePerSecond=10000` só deixa o `SimulationEngine` fazer mais aritmética; o mesmo cenário real tentaria publicar 10 mil mensagens por segundo de verdade nesta máquina. `ScenarioValidator.validateRealExecution` existe só para isso — não é uma regra de negócio do domínio, é uma proteção de infraestrutura.
- **`SimulationPersistence.complete` ganhou uma sobrecarga por totais.** A versão original só aceitava `SimulationState` (estado interno do motor simulado). `RealSimulationService` não tem — nem deveria ter — um `SimulationState`; passa os cinco totais (`produced`, `ok`, `dlq`, `dropped`, `retries`) diretamente. A versão com `SimulationState` virou um `complete(...)` que delega para a nova.
- **Sem overlay "Simulado × Real".** O `PLAN.md` original sugeria um painel comparando lado a lado; cortado do escopo — o objetivo desta fase reduzida é ter o caminho real funcionando corretamente, não uma segunda camada de visualização. O relatório mostra uma tag "Simulado"/"Real" no cabeçalho; comparar as duas séries manualmente (rodar o mesmo cenário nos dois modos e olhar os dois relatórios) já é possível com o que existe.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `service/real/MessageBrokerPort` | Porta: contrato que qualquer broker real precisa cumprir | — |
| `service/real/RabbitMqBrokerAdapter` | Único adaptador real hoje — RabbitAdmin/RabbitTemplate/SimpleMessageListenerContainer | spring-amqp |
| `service/real/MessageProcessor`, `ProcessResult` | Callback que decide o destino de cada mensagem consumida | — |
| `service/real/RealMetrics` | Contadores cumulativos + por-tick (produzido/ok/falha/dropped, latências) | — |
| `service/real/RealSimulationService` | Orquestra publish (scheduler) + consumo + loop de ticks + SSE | `MessageBrokerPort`, `SimulationRunService`, `SimulationPersistence` |
| `service/real/BrokerHealthService` | `UP`/`DOWN`/`NOT_CONFIGURED` por broker | `MessageBrokerPort` |
| `controller/BrokerController` | `GET /api/brokers/health` | `BrokerHealthService` |
| `domain/model/ExecutionMode` | `SIMULATED` \| `REAL`, campo do `Scenario` | — |
| `service/scenario/ScenarioValidator#validateRealExecution` | Restringe REAL a RabbitMQ + tetos de taxa/duração | — |
| `config/RabbitConfig` | Bean `RabbitAdmin` (spring-boot-starter-amqp não cria um sozinho) | — |
| `controller/RunController` | Decide `LiveSimulationService` vs `RealSimulationService` por `scenario.executionMode` | ambos |
| `pages/ScenariosPage/ScenarioForm.tsx` | Toggle Simulado/Real, desabilitado fora de RabbitMQ | — |
| `pages/ReportPage/ReportPanel.tsx` | Tag "Simulado"/"Real", esconde "Resultado instantâneo" para REAL | — |

## 5. Fluxo de uma requisição

**Criar e rodar um cenário real:**

1. `POST /api/scenarios` com `broker=RABBITMQ`, `executionMode=REAL` — `ScenarioValidator` recusa qualquer outro broker ou taxas/duração acima dos tetos.
2. `POST /api/scenarios/{id}/runs?mode=LIVE` — igual a um cenário simulado; cria a `SimulationRun` com status `RUNNING`. (`mode=INSTANT` é rejeitado para `executionMode=REAL` — não existe "resultado instantâneo" de um broker real.)
3. `GET /api/runs/{id}/stream` — `RunController` lê `run.getScenario().getExecutionMode()` e delega a `RealSimulationService` em vez de `LiveSimulationService`.
4. `RealSimulationService.runLoop`: declara a fila (`msgsim-{runId}`), registra o consumidor, começa a publicar em ritmo real (1x/segundo, `ratePerSecond` mensagens por vez) via `ScheduledExecutorService`. A cada segundo, monta um `TickResult` com a profundidade real da fila (`RabbitAdmin`/`channel.queueDeclarePassive`) e os percentis de latência das mensagens processadas naquele segundo, emite via SSE, persiste em lotes de 10 ticks.
5. Ao fim da duração (ou `POST /api/runs/{id}/stop`): para o scheduler de publish, espera meio segundo para as últimas mensagens em voo, para o consumidor, purga e deleta a fila (`finally`, sempre executa) — a próxima execução do mesmo cenário começa com fila limpa.

## 6. Trechos comentados

```java
// RabbitMqBrokerAdapter.handleMessage — por que ack manual sempre, mesmo em RETRY/DLQ:
// republicar-e-ackar (em vez de nack-com-requeue) é a única forma de reescrever
// o header de contagem de tentativas — o RabbitMQ não deixa alterar headers
// de uma mensagem já em fila, só de uma nova publicação.
case RETRY -> {
    channel.basicAck(deliveryTag, false);
    publish(queueName, message.getBody(), retryCount + 1);
}
```

```java
// RealSimulationService.processMessage — por que o "processamento" é um
// Thread.sleep(scenario.getProcessingMs()) e a falha é um Random, mesmo
// falando com um broker de verdade: o que este estudo mede é o transporte
// (fila, rede, concorrência), não um sistema de produção real por trás dela.
// Simular o trabalho e o erro mantém o cenário comparável ao mesmo cenário
// rodado no modo SIMULATED — só a "canalização" muda.
```

## 7. Como testar manualmente

```sh
docker compose -f docker-compose.dev.yml up -d rabbitmq

TOKEN=$(curl -s -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}' | jq -r .token)

curl -s http://localhost:1337/api/brokers/health -H "Authorization: Bearer $TOKEN"
# {"KAFKA":"NOT_CONFIGURED","RABBITMQ":"UP","SQS":"NOT_CONFIGURED"}

SCENARIO_ID=$(curl -s -X POST http://localhost:1337/api/scenarios \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"RabbitMQ real","broker":"RABBITMQ","ratePerSecond":10,"consumers":2,
       "processingMs":50,"failurePct":10,"maxRetries":2,"messageSizeKb":1,
       "durationSeconds":8,"queueCapacity":0,"dlqEnabled":true,"burstEnabled":false,
       "executionMode":"REAL"}' | jq -r .id)

RUN_ID=$(curl -s -X POST "http://localhost:1337/api/scenarios/$SCENARIO_ID/runs?mode=LIVE" \
  -H "Authorization: Bearer $TOKEN" | jq -r .id)

curl -sN "http://localhost:1337/api/runs/$RUN_ID/stream" -H "Authorization: Bearer $TOKEN"

# fila não deve sobrar depois que o evento "finished" chegar:
curl -s -u guest:guest http://localhost:15672/api/queues
```

Na UI: criar um cenário com broker RabbitMQ, ligar o toggle "Execução: Real", "Rodar (RabbitMQ real)" — acompanhar os ticks chegando e o backlog real. Trocar o broker para Kafka/SQS no formulário e confirmar que o toggle desliga sozinho e fica desabilitado.

## 8. O que eu aprendi / erros cometidos

- **`spring-boot-starter-amqp` não cria um bean `RabbitAdmin` sozinho** neste projeto — só `ConnectionFactory`/`RabbitTemplate`. `@SpringBootTest` do `ScenarioControllerTest` falhava com `NoSuchBeanDefinitionException` para `RabbitAdmin` assim que `RabbitMqBrokerAdapter` (que injeta `RabbitAdmin`) entrou no contexto. Fix: `RabbitConfig` declarando o bean explicitamente. Only found rodando `mvn test`, não em `mvn compile` — outro caso de bug que só aparece com o contexto Spring de verdade subindo.
- **Testes existentes quebraram silenciosamente na compilação**, não em tempo de execução: adicionar `executionMode` ao record `ScenarioRequest`/`ScenarioResponse` quebrou `ScenarioValidatorTest` e `ScenarioControllerTest`, que construíam os records posicionalmente. `mvn compile` passa (só afeta `main`); só `mvn test-compile` pega. Lição prática: mudar a assinatura de um record usado em testes exige rodar `test-compile`, não só `compile`, antes de seguir em frente.
- **Testado de ponta a ponta com o RabbitMQ real** (não só com testes unitários): rodei uma execução completa via `curl -sN .../stream`, confirmei retries e ticks corretos, e uma segunda execução parada no meio via `POST /stop` — confirmando que o `finally` limpa a fila nos dois casos (fim normal e parada manual). `GET http://localhost:15672/api/queues` (API de management do RabbitMQ) confirmou zero filas residuais depois de ambas.
