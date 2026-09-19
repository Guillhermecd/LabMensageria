# Simulador de Mensageria

Simulador didático de filas de mensageria — modela Kafka, RabbitMQ e SQS sob a mesma carga, roda a simulação (instantânea ou ao vivo, tick a tick via SSE), e gera um relatório analítico comparando os três brokers com narrativa, insights e conclusão em texto.

Construído sobre a base do template BIMD (`Main ReadMe.md`): Java 21/Spring Boot 3 + PostgreSQL no backend, React/TypeScript/Vite/Ant Design no frontend, autenticação JWT stateless.

---

## Rodando localmente

```sh
# infraestrutura (Postgres, MinIO, Mailpit)
docker compose -f docker-compose.dev.yml up -d postgres minio mailpit

# backend
cd backend
cp .env.example .env   # ajustar JWT_SECRET (mín. 32 chars) se necessário
./mvnw spring-boot:run

# frontend
cd frontend
cp .env.example .env
npm install
npm run dev
```

Login inicial: `INITIAL_USER_EMAIL` / `INITIAL_USER_PASSWORD` do `.env` (padrão `admin@oaksd.local` / `ChangeMe123!`).

> Se a porta padrão do Postgres (`5432`) já estiver em uso na sua máquina, o `docker-compose.dev.yml` deste projeto mapeia para `5440` — ajuste `SPRING_DATASOURCE_URL` no `.env` do backend de acordo. Detalhes em `ARCHITECTURE.md`.

### Checklist antes de um Pull Request

```sh
cd backend && ./mvnw verify
cd frontend && npm run lint && npm run build
```

---

## Roteiro de estudo

O projeto é objeto de estudo — cada fase do `PLAN.md` deixou uma explicação do que foi feito e por quê. Ordem recomendada de leitura:

| # | Doc | Assunto |
|---|---|---|
| 00 | [`docs/00-foundation.md`](docs/00-foundation.md) | Fundação: JWT stateless, Postgres/Flyway, estrutura do template |
| 01 | [`docs/01-scenarios.md`](docs/01-scenarios.md) | CRUD de cenários e validação específica por broker |
| 02 | [`docs/02-simulation-engine.md`](docs/02-simulation-engine.md) | Motor de simulação: anatomia de um tick, `BrokerBehavior` (Strategy) |
| 03 | [`docs/03-report-dashboard.md`](docs/03-report-dashboard.md) | Dashboard de resultados, KPIs, gráficos |
| 04 | [`docs/04-live-simulation.md`](docs/04-live-simulation.md) | Execução ao vivo via SSE, por que não WebSocket |
| 05 | [`docs/05-tradeoff-report.md`](docs/05-tradeoff-report.md) | Modelo analítico M/M/c simplificado, pontuação de trade-off |
| 06 | [`docs/06-comparison.md`](docs/06-comparison.md) | Comparação lado a lado de dois cenários |
| 07 | [`docs/07-delivery.md`](docs/07-delivery.md) | Storage (MinIO), export de relatório, histórico, retrospectiva |
| 08 | [`docs/08-real-brokers.md`](docs/08-real-brokers.md) | Execução real contra RabbitMQ (ports and adapters), simulado × real |

Decisões que atravessam mais de uma fase estão em `docs/adr/` (Architecture Decision Records). Termos do domínio (backlog, lag, DLQ, visibility timeout, p95...) estão em `docs/glossary.md`.

Decisões de stack e desvios de infraestrutura local: [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## Estrutura

```text
msgsim/
  backend/    Java 21 / Spring Boot 3 / PostgreSQL
  frontend/   React + TypeScript + Vite + Ant Design
  docs/       Documentação de estudo, uma por fase, + ADRs
  docker-compose.dev.yml
  ARCHITECTURE.md
  PLAN.md     Plano de projeto original
```
