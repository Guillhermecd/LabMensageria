# BIMD Template

Template oficial para novos sistemas da BIMD Software Solutions.

Este repositório é um monorepo com frontend React/Vite e backend à sua escolha: **Node.js/Sails.js** ou **Java/Spring Boot**. Funciona de forma independente em desenvolvimento local e foi organizado para integração futura ao repositório `infra` da BIMD sem mudanças estruturais.

---

## Escolha da Stack

Antes de iniciar um novo projeto, responda as duas perguntas abaixo. As respostas definem a estrutura do backend e os serviços locais necessários.

### 1. Qual backend?

| Critério | Node.js / Sails.js | Java / Spring Boot |
|---|---|---|
| Prototipagem rápida | ✅ | ❌ |
| Real-time / WebSocket | ✅ | ⚠️ |
| APIs de alta carga | ⚠️ | ✅ |
| Regras de negócio complexas | ⚠️ | ✅ |
| Familiaridade do time | Por projeto | Por projeto |

### 2. Qual banco de dados?

| Critério | MongoDB | PostgreSQL |
|---|---|---|
| Dados sem esquema fixo | ✅ | ❌ |
| Documentos aninhados / flexíveis | ✅ | ⚠️ |
| Relações complexas entre entidades | ⚠️ | ✅ |
| Transações ACID | ⚠️ | ✅ |
| Relatórios e queries analíticas | ⚠️ | ✅ |

> Anote as duas escolhas no `ARCHITECTURE.md` do projeto, junto com a justificativa.

---

## Combinações Suportadas

| Backend | Banco | ORM / Driver |
|---|---|---|
| Node.js / Sails.js | MongoDB | `sails-mongo` |
| Node.js / Sails.js | PostgreSQL | `sails-postgresql` |
| Java / Spring Boot | MongoDB | `spring-boot-starter-data-mongodb` |
| Java / Spring Boot | PostgreSQL | `spring-boot-starter-data-jpa` + Hibernate |

---

## Estrutura do Repositório

```text
bimd-template/
  backend/               # Node.js/Sails.js OU Java/Spring Boot (escolher um)
  frontend/              # React + TypeScript + Vite
  docker-compose.dev.yml
  ARCHITECTURE.md
  README.md
```

---

## Serviços Locais

Os serviços disponíveis variam conforme o banco escolhido.

### Com MongoDB

```sh
docker compose -f docker-compose.dev.yml up -d mongo minio mailpit
```

- MongoDB em `mongodb://localhost:27017`
- MinIO em `http://localhost:9000` — Console: `http://localhost:9001`
- Mailpit SMTP em `127.0.0.1:1025` — UI: `http://localhost:8025`

### Com PostgreSQL

```sh
docker compose -f docker-compose.dev.yml up -d postgres minio mailpit
```

- PostgreSQL em `localhost:5432`
- MinIO em `http://localhost:9000` — Console: `http://localhost:9001`
- Mailpit SMTP em `127.0.0.1:1025` — UI: `http://localhost:8025`

> MongoDB e PostgreSQL são obrigatórios no boot do backend. Falha no MinIO ou Mailpit gera apenas warning, pois storage e e-mail podem estar desabilitados ou apontados para outro serviço.

---

## Clean Code e SOLID

Todo código produzido neste template deve seguir os princípios abaixo. Eles se aplicam a ambas as stacks de backend e ao frontend.

### Princípios SOLID

| Princípio | Resumo | Exemplo prático |
|---|---|---|
| **S** — Single Responsibility | Cada classe ou módulo tem uma única razão para mudar | Um `AuthService` cuida só de autenticação, não de envio de e-mail |
| **O** — Open/Closed | Aberto para extensão, fechado para modificação | Adicionar novo método de pagamento sem alterar o existente |
| **L** — Liskov Substitution | Subtipos devem poder substituir seus tipos base sem quebrar o sistema | Um `AdminService` que estende `UserService` deve respeitar o mesmo contrato |
| **I** — Interface Segregation | Interfaces pequenas e específicas, não genéricas e inchadas | Separar `IReadable` de `IWritable` em vez de uma `IRepository` com tudo |
| **D** — Dependency Inversion | Dependa de abstrações, não de implementações concretas | Services recebem dependências por injeção, não as instanciam internamente |

### Regras Gerais de Clean Code

- **Nomes revelam intenção.** `getUserByEmail` é melhor que `getUser` ou `fetch`.
- **Funções fazem uma coisa.** Se precisar de "e" para descrever o que ela faz, divida.
- **Sem comentários explicando código ruim.** Reescreva o código; use comentários só para o "porquê".
- **Sem números mágicos.** Use constantes nomeadas: `MAX_LOGIN_ATTEMPTS = 5`.
- **Tamanho de arquivo.** Máximo de ~150 linhas em Node.js e ~200 linhas em Java. Se exceder, é sinal de responsabilidade dupla.
- **Tratamento de erro explícito.** Nunca silenciar exceções com `catch` vazio.
- **Sem código morto.** Remova funções, variáveis e imports não utilizados. O controle de versão guarda o histórico.

### Nomeclatura

- todas as variaveis, clases e objetos devem ter seguir um padrão de nomeclatura em ingles.

---

## Backend — Node.js / Sails.js

### Tecnologias

- Node.js
- Sails.js
- JWT + bcrypt
- Dayjs
- Nodemailer
- AWS SDK v3 (compatível com S3/MinIO)
- `sails-mongo` (MongoDB) ou `sails-postgresql` (PostgreSQL)

### Estrutura do Projeto

```text
backend/
  api/
    controllers/         # Um controller por domínio — apenas camada HTTP
    models/              # Um model por entidade — apenas schema
    policies/            # Middleware de autenticação e autorização
    services/            # Lógica de negócio — um service por domínio
    helpers/             # Funções utilitárias puras — sem efeitos colaterais
  config/
    routes.js            # Todas as declarações de rotas
    policies.js          # Vínculos de policies
    datastores.js        # Configuração do banco de dados
    custom.js            # Configurações customizadas do Sails
  .env.example
  package.json
```

### Clean Code por Camada (Node.js)

- **Controller:** recebe a requisição, valida a entrada e chama o service. Retorna a resposta. Nenhuma lógica de negócio.
- **Service:** contém toda a lógica de negócio. Não importa `req` nem `res`. Não acessa o banco diretamente — usa o model.
- **Model:** define o schema e os atributos. Sem métodos com regras de negócio. Sem chamadas a outros models.
- **Policy:** verifica autenticação e autorização. Não executa lógica de domínio.
- **Helper:** função pura que recebe entrada e retorna saída. Sem dependências externas, sem efeitos colaterais.

### Setup

```sh
cd backend
cp .env.example .env
npm install
npm run dev
```

### Scripts

- `npm run dev` — inicia o Sails com nodemon
- `npm start` — inicia em modo produção
- `npm run lint` — executa o ESLint
- `npm run format` — aplica o Prettier
- `npm run format:check` — valida o Prettier

### Variáveis de Ambiente

**Com MongoDB:**

```env
PORT=1337
MONGO_URL=mongodb://localhost:27017/bimd_template
```

**Com PostgreSQL:**

```env
PORT=1337
DB_HOST=localhost
DB_PORT=5432
DB_NAME=bimd_template
DB_USER=postgres
DB_PASSWORD=postgres
```

**Comuns a ambos:**

```env
JWT_SECRET=change-me
FRONTEND_BASE_URL=http://localhost:5173
CORS_ORIGINS=http://localhost:5173

INITIAL_USER_EMAIL=admin@oaksd.local
INITIAL_USER_PASSWORD=ChangeMe123!
INITIAL_USER_NAME=Administrador

EMAIL_ENABLED=true
EMAIL_FROM=no-reply@oaksd.com
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_SECURE=false

S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_BUCKET=bimd-template
S3_ACCESS_KEY_ID=minioadmin
S3_SECRET_ACCESS_KEY=minioadmin
S3_FORCE_PATH_STYLE=true
S3_PRESIGNED_URL_EXPIRES_IN=900
```

---

## Backend — Java / Spring Boot

### Tecnologias

- Java 21
- Spring Boot 3
- Spring Security (JWT stateless)
- JavaMailSender (Mailpit em desenvolvimento)
- AWS SDK v2 (compatível com S3/MinIO)
- Lombok
- MapStruct
- `spring-boot-starter-data-mongodb` (MongoDB) ou `spring-boot-starter-data-jpa` + Hibernate (PostgreSQL)

### Estrutura do Projeto

```text
backend/
  src/
    main/
      java/com/bimd/template/
        config/            # Beans do Spring, segurança, CORS, cliente S3
        controller/        # Controllers REST — apenas camada HTTP
        service/           # Lógica de negócio — um service por domínio
        repository/        # Interfaces Spring Data — sem lógica customizada
        domain/
          model/           # Entidades: @Document (MongoDB) ou @Entity (PostgreSQL)
          dto/             # DTOs de requisição e resposta
          mapper/          # Mappers MapStruct (model ↔ DTO)
        exception/         # Exceções customizadas e @ControllerAdvice
        util/              # Classes utilitárias estáticas puras
        security/          # Filtro JWT, UserDetailsService, token provider
      resources/
        application.yml
        application-dev.yml
        application-prod.yml
    test/
      java/com/bimd/template/
        controller/        # Testes de integração (MockMvc)
        service/           # Testes unitários (Mockito)
  pom.xml
  .env.example
```

### Clean Code por Camada (Java)

- **Controller:** declara a rota e delega ao service imediatamente. Sem lógica além de validação de entrada com Bean Validation. Nunca retorna tipos de domínio — sempre DTOs.
- **Service:** dono de todas as regras de negócio. Nunca retorna `ResponseEntity` ou `HttpStatus`. Recebe dependências por injeção (`@RequiredArgsConstructor`). Aplica SRP: um service por domínio.
- **Repository:** interface Spring Data pura. Sem lógica de negócio. Queries customizadas usam `@Query` quando JPQL/MQL não é suficiente.
- **Domain / Model:** entidade de persistência apenas. Sem lógica de negócio embutida. Mapeada pelo MapStruct para DTO antes de sair da camada de service.
- **DTO:** trafega apenas os dados necessários para cada operação. Nunca expõe a entidade de domínio diretamente na resposta HTTP.
- **Mapper:** converte entre model e DTO via MapStruct. Sem mapeamento manual espalhado pelos services.
- **Exception:** exceções nomeadas e tipadas. Tratamento centralizado no `@ControllerAdvice`. Nunca capturar `Exception` genericamente sem relançar ou logar.

### Setup

```sh
cd backend
cp .env.example .env
./mvnw spring-boot:run
```

Para produção:

```sh
./mvnw clean package -DskipTests
java -jar target/bimd-template.jar
```

### Variáveis de Ambiente

**Com MongoDB:**

```env
SERVER_PORT=1337
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/bimd_template
```

**Com PostgreSQL:**

```env
SERVER_PORT=1337
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bimd_template
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

**Comuns a ambos:**

```env
JWT_SECRET=change-me
JWT_EXPIRATION_MS=86400000
FRONTEND_BASE_URL=http://localhost:5173
CORS_ORIGINS=http://localhost:5173

INITIAL_USER_EMAIL=admin@oaksd.local
INITIAL_USER_PASSWORD=ChangeMe123!
INITIAL_USER_NAME=Administrador

EMAIL_ENABLED=true
EMAIL_FROM=no-reply@oaksd.local
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_SECURE=false

S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_BUCKET=bimd-template
S3_ACCESS_KEY_ID=minioadmin
S3_SECRET_ACCESS_KEY=minioadmin
S3_FORCE_PATH_STYLE=true
S3_PRESIGNED_URL_EXPIRES_IN=900
```

---

## Autenticação

A autenticação é stateless com JWT. O contrato é o mesmo independentemente da stack escolhida.

### Rotas Públicas

| Método | Caminho |
|--------|---------|
| POST | `/api/auth/register` |
| POST | `/api/auth/login` |
| GET | `/api/auth/verify-email` |
| POST | `/api/auth/resend-verification` |
| POST | `/api/auth/forgot-password` |
| POST | `/api/auth/reset-password` |

### Rotas Privadas

| Método | Caminho |
|--------|---------|
| GET | `/api/profile` |
| PATCH | `/api/profile` |
| PATCH | `/api/profile/email` |
| PATCH | `/api/profile/password` |
| POST | `/api/profile/image` |
| GET | `/api/storage/presigned-url` |

O logout é tratado apenas no frontend:

```js
localStorage.clear();
// redirecionar para /login
```

---

## Padrão de Datas

Toda data trafegada entre frontend e backend deve ser uma string ISO 8601:

```
2026-06-21T18:00:00.000Z
```

Nunca usar timestamps numéricos, datas serializadas automaticamente ou formatos locais. O frontend usa Dayjs para manipulação. Ambas as stacks armazenam `createdAt` e `updatedAt` como ISO strings.

---

## Storage

Uploads usam integração S3 compatível com MinIO. A foto de perfil é o exemplo base, mas os serviços de storage foram criados para reutilização em novos módulos.

Operações base:

- Upload
- Download
- URL pré-assinada

---

## Frontend

### Tecnologias

- React
- TypeScript
- Vite
- Ant Design (biblioteca principal de UI)
- React Router DOM
- React Hook Form
- Dayjs

### Estrutura do Projeto

```text
frontend/
  src/
    api/
      modules/
        api.ts               # Cliente HTTP central — exporta `api` e `authStorage`
        types.ts             # Tipos compartilhados entre múltiplos serviços
        auth.service.ts      # Chamadas de API do domínio de autenticação
        profile.service.ts
        storage.service.ts
        # um arquivo por domínio
    assets/                  # Arquivos estáticos: imagens, ícones, fontes, SVGs
    components/              # Componentes compartilhados usados em 2+ páginas
      ui/                    # Componentes apresentacionais genéricos
      layout/                # AppShell, Sidebar, Header, Footer
    hooks/                   # Hooks customizados compartilhados usados em 2+ páginas
    pages/
      LoginPage/
        index.tsx
        LoginPage.tsx
        CreateAccountModal.tsx   # Usado apenas pela LoginPage
        ForgotPasswordModal.tsx  # Usado apenas pela LoginPage
        useLoginForm.ts          # Hook usado apenas pela LoginPage
    router.tsx               # Todas as declarações de rotas
    theme.ts                 # Tokens do ConfigProvider do Ant Design
    main.tsx
    App.tsx
  index.html
  vite.config.ts
  tsconfig.json
  .env.example
```

### Clean Code por Camada (Frontend)

- **Page:** orquestra layout e estado da tela. Não chama `fetch`, `axios` ou URLs diretamente. Não contém lógica de negócio.
- **Service (`src/api/modules/`):** único ponto de contato com o backend. Importa apenas o `api` central. Concentra todas as URLs do seu domínio. Um arquivo por domínio.
- **Hook compartilhado (`src/hooks/`):** encapsula lógica reutilizada em 2+ páginas (ex: `useDebounce`, `useAuth`). Sem JSX.
- **Hook de página:** encapsula lógica específica de uma tela (ex: `useLoginForm`). Fica dentro da pasta da página.
- **Componente compartilhado (`src/components/`):** sem dependência de domínio. Recebe tudo via props. Sem chamadas de API.
- **Componente de página:** filho direto da page. Pode consumir contexto, mas não chama API diretamente.

### Regras Gerais (Frontend)

- Todos os formulários usam React Hook Form com `Controller`.
- Sem arquivos `.css`. Estilos pontuais usam props `style`/`styles` inline.
- Tokens globais, cores e configurações de fonte ficam em `src/theme.ts` via `ConfigProvider`.
- Gerenciamento de estado global é decidido por projeto — nenhum padrão é imposto.
- Tipos compartilhados entre serviços ficam em `types.ts`. Tipos de um único serviço ficam no arquivo do próprio serviço.

### Tema

Todo projeto define seu tema em `src/theme.ts` com suporte a **modo claro e escuro**, usando os algoritmos do Ant Design. A estrutura padrão exporta os tokens dos dois modos, as cores semânticas e o gradiente da marca como constantes reutilizáveis — nunca espalhe hexadecimais pelo código.

```ts
// src/theme.ts
import { theme as antdTheme, type ThemeConfig } from 'antd';

// Gradiente da marca — usar apenas em botões primários, barras de
// progresso, cards hero e fundo da tela de login. Nunca em superfícies
// de leitura (cards de dados, tabelas, textos).
export const BRAND_GRADIENT =
  'linear-gradient(135deg, #FBBF24 0%, #F97316 55%, #EA580C 100%)';

// Cores semânticas — não usar a cor primária para significado
// financeiro. O tom carmim (e não vermelho puro) evita confusão
// com o laranja da marca.
export const semantic = {
  light: {
    income: { text: '#1B7A3D', bg: '#E6F4EA' },
    expense: { text: '#C2274B', bg: '#FCE9EC' },
  },
  dark: {
    income: { text: '#5DC98A', bg: '#1E3326' },
    expense: { text: '#F08CA4', bg: '#3A1F26' },
  },
} as const;

export const lightTheme: ThemeConfig = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#F97316',
    colorLink: '#EA580C',
    colorText: '#2B2118',
    colorBgBase: '#FFFFFF',
    colorBgLayout: '#FAF6F1',   // fundo quente, integra com a primária
    borderRadius: 12,
  },
};

export const darkTheme: ThemeConfig = {
  algorithm: antdTheme.darkAlgorithm,
  token: {
    colorPrimary: '#FB923C',    // tom mais claro para contraste no escuro
    colorLink: '#FDBA74',
    colorBgBase: '#2C231B',     // cards
    colorBgLayout: '#211A14',   // fundo da página
    colorBorder: '#3A2F25',
    borderRadius: 12,
  },
};
```

A troca de tema é feita por um hook `useTheme` (Context + `localStorage`, com `prefers-color-scheme` como padrão inicial) que alterna o `ThemeConfig` passado ao `ConfigProvider`.

Regras:

- **Contraste AA nos dois modos.** Texto sobre a cor primária é sempre branco; a primária como cor de texto só em tamanhos ≥ 14px bold.
- **Cores semânticas nunca usam a cor primária.** Entrada/positivo em verde, saída/negativo em carmim, nos tokens de `semantic`.
- **Gradiente é destaque, não tapete.** Vale a lista de usos permitidos no comentário do `BRAND_GRADIENT`.
- A paleta laranja acima é a referência do template; projetos com identidade própria substituem os valores mantendo a mesma estrutura (`BRAND_GRADIENT`, `semantic`, `lightTheme`, `darkTheme`).

Só configure uma fonte própria no tema se o projeto incluir e carregar o asset da fonte.

### Exemplo de Serviço

```ts
// src/api/modules/auth.service.ts
import { api } from './api';
import type { User } from './types';

type AuthResponse = {
  token: string;
  user: User;
};

export const AuthService = {
  login(payload: { email: string; password: string }) {
    return api<AuthResponse>('/auth/login', {
      method: 'POST',
      auth: false,
      body: JSON.stringify(payload),
    });
  },
};
```

### Setup

```sh
cd frontend
npm install
npm run dev
```

### Scripts

- `npm run dev` — inicia o Vite
- `npm run build` — executa TypeScript e build
- `npm run lint` — executa o ESLint
- `npm run format` — aplica o Prettier
- `npm run format:check` — valida o Prettier

---

## Convenções de Git

### Nomenclatura de Branches

```
<type>/<descricao-curta>
```

| Tipo | Quando usar |
|------|-------------|
| `feat` | Nova funcionalidade |
| `fix` | Correção de bug |
| `chore` | Configuração, tooling, dependências |
| `refactor` | Reestruturação de código sem mudança de comportamento |
| `docs` | Apenas documentação |
| `test` | Adição ou correção de testes |
| `hotfix` | Correção urgente em produção |

Exemplos:

```
feat/user-profile-upload
fix/jwt-expiration-header
chore/update-dependencies
docs/readme-stack-selection
```

### Mensagens de Commit — Conventional Commits

```
<type>(<scope>): <descrição curta no imperativo>
```

O scope é opcional, mas recomendado. Use o nome do domínio ou módulo.

```
feat(auth): add email verification on register
fix(profile): correct image upload path on MinIO
chore(deps): update sails-mongo to 4.0.1
refactor(storage): extract presigned URL logic to helper
docs(readme): add Java/Spring Boot backend section
test(auth): add unit tests for JWT token provider
```

Regras:
- Linha de assunto com no máximo 72 caracteres.
- Usar modo imperativo: "add", "fix", "update" — não "added", "fixed", "updated".
- Sem ponto final na linha de assunto.
- Corpo é opcional. Use para explicar o *porquê*, não o *o quê*.

### Fluxo de Trabalho

```
main          — pronto para produção, protegido
└── develop   — branch de integração
    └── feat/sua-feature
    └── fix/seu-fix
```

1. Criar branch a partir de `develop`.
2. Abrir Pull Request apontando para `develop`.
3. Mínimo de uma revisão exigida antes do merge.
4. Squash merge preferível para manter o histórico do `develop` limpo.
5. `main` recebe merges do `develop` apenas em releases planejadas.

### Título do PR

Seguir o mesmo formato do Conventional Commits:

```
feat(payments): add Pix payment method
```

---

## Integração com Infra

O template respeita os contratos do repositório `infra`:

- `frontend/` e `backend/` na raiz do projeto.
- Backend escutando em `PORT=1337` (Node.js) ou `SERVER_PORT=1337` (Spring Boot).
- Frontend buildado para `frontend/dist`.
- Nginx de produção servindo o frontend e encaminhando `/api/` para o backend.
- Banco de dados via `MONGO_URL` / `SPRING_DATA_MONGODB_URI` (MongoDB) ou `SPRING_DATASOURCE_URL` / variáveis `DB_*` (PostgreSQL).
- Storage via `S3_*`.
- SMTP via `SMTP_*` e `EMAIL_*`.
- CORS via `CORS_ORIGINS`.

Para integrar no futuro, clone ou copie a pasta `infra/` ao lado de `frontend/` e `backend/`, configure `infra/.env` e execute `infra/deploy.sh`.

---

## Checklist de Validação

Antes de abrir um Pull Request, confirme:

```sh
# Backend Node.js
cd backend && npm run lint

# Backend Java
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run lint && npm run build
```

Não há testes E2E, testes visuais, Playwright, Cypress ou snapshots neste template.
