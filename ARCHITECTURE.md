# ARCHITECTURE.md — Simulador de Mensageria

## Decisões de stack

| Decisão | Escolha | Justificativa |
|---|---|---|
| Backend | Java 21 / Spring Boot 3 | Regras de negócio complexas (motor de simulação, modelo analítico); API de carga (rodar N ticks) |
| Banco | PostgreSQL | Relações fixas (Scenario → SimulationRun → Tick/Event); relatórios e queries analíticas sobre execuções |
| ORM | `spring-boot-starter-data-jpa` + Hibernate, `ddl-auto=validate` + Flyway | Schema controlado por migration, nunca por auto-DDL |
| Frontend | React + TypeScript + Vite + Ant Design + React Hook Form + Dayjs | Definição do Main ReadMe.md (template BIMD) |
| Brokers reais | Kafka, RabbitMQ, LocalStack (SQS) via Docker | Fase 8 — testar os três localmente sem conta AWS |
| Live run | SSE (`SseEmitter`) | Simulação ao vivo sem WebSocket (Fase 4) |
| Estado global (frontend) | React Context (`useAuth`, `useTheme`) — sem Redux | Escopo pequeno, README deixa livre |

O projeto **não** parte de um clone do template `bimd-template` — não localizado/disponível no momento do bootstrap. A estrutura, convenções e stack foram recriadas do zero seguindo à risca as regras de `Main ReadMe.md` (Clean Code, SOLID, camadas, nomenclatura, autenticação JWT stateless, padrão de datas ISO 8601, sem CSS solto no frontend).

## Escopo da Fase 0 (fundação)

O `Main ReadMe.md` lista 12 rotas de auth/profile assumindo que viriam prontas do template clonado. Como a fundação foi construída do zero, a Fase 0 implementa apenas o necessário para "login funciona" com JWT stateless real:

**Implementado nesta fase:**
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/profile`
- `PATCH /api/profile`
- Filtro JWT stateless (`JwtAuthenticationFilter` + `JwtService`)
- Seed do usuário inicial via `INITIAL_USER_*` (`InitialUserSeeder`)

**Deferido para uma fase futura nomeada (não bloqueia Fases 1–7):**
- `GET /api/auth/verify-email`
- `POST /api/auth/resend-verification`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `PATCH /api/profile/email`
- `PATCH /api/profile/password`
- `POST /api/profile/image`
- `GET /api/storage/presigned-url`

Motivo do adiamento: essas rotas dependem de integração real com Mailpit (fluxo de e-mail) e MinIO/S3 (upload), que nenhuma fase do `PLAN.md` (Fases 1–8, motor de simulação e brokers) bloqueia. Serão implementadas junto com a Fase 7 (Storage/Export) ou antes, se necessário.

## Desvios de infraestrutura local

- **Porta do PostgreSQL**: o `docker-compose.dev.yml` mapeia o Postgres para `5440` no host (não `5432`), porque esta máquina já tem instâncias nativas de PostgreSQL ocupando `5432` **e** `5433`. Dentro da rede Docker o Postgres continua ouvindo em `5432` normalmente — só o mapeamento externo mudou. Isso é uma particularidade de ambiente local, documentada aqui para não ser confundida com uma exigência do template.
- **Maven wrapper**: gerado via `mvn -N wrapper:wrapper -Dmaven=3.9.6`, versionado em `backend/.mvn/`.
- **Lombok**: fixado em `1.18.48` (acima do padrão do `spring-boot-starter-parent`) — a versão herdada não suporta o JDK 25 instalado nesta máquina para anotações em tempo de compilação. `maven.compiler.release=21` garante que o bytecode gerado permaneça em Java 21.

## Tokens de tema (frontend)

`semantic` em `src/theme.ts` usa `ok / warning / danger / info` (não `income / expense` do exemplo genérico do Main ReadMe.md), porque o domínio é mensageria, não financeiro. A estrutura (`BRAND_GRADIENT`, `semantic`, `lightTheme`, `darkTheme`, suporte a claro/escuro, contraste AA) segue o padrão do template.

## Protótipo de referência

O protótipo funcional (`Simulador de Mensageria.dc.html`, projeto claude.ai/design) foi extraído e salvo em `.prototype/` (fora do controle de versão — ver `.gitignore`) como referência para as Fases 1, 2 e 5: engine de simulação (`step`), modelo analítico (`model`/`tradeoffs`), narrativa e conclusão. O mapeamento de cada função do protótipo para as classes Java está em `PLAN.md`, seção 1.
