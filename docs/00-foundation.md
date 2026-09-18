# 00 — Fundação

## 1. Objetivo

Ter o repositório de pé: backend Java/Spring Boot autenticando com JWT, PostgreSQL migrado por Flyway, frontend React servindo a tela de login, e todos os checks (`./mvnw verify`, `npm run lint && npm run build`) verdes.

## 2. Conceito de mensageria estudado

Nenhum ainda — esta fase é infraestrutura pura. O estudo de mensageria começa na Fase 2 (motor de simulação).

## 3. Decisões de design

- **JWT stateless, sem sessão no servidor.** O token carrega apenas o `subject` (email) e expiração; cada requisição autenticada é validada de forma independente pelo `JwtAuthenticationFilter`. Aplica o princípio de Dependency Inversion: o filtro depende da abstração `UserDetailsService`, não de uma implementação concreta.
- **Flyway + `ddl-auto=validate`, nunca `update`.** O schema é sempre uma decisão explícita (migration versionada), nunca inferido pelo Hibernate. Evita divergência silenciosa entre entidade e banco.
- **DTOs em todos os endpoints, nunca a entidade `User` direto.** Single Responsibility: `User` cuida de persistência, `UserResponse`/`RegisterRequest` cuidam do contrato HTTP. Conversão via MapStruct (`UserMapper`), sem mapeamento manual espalhado.
- **`BusinessException` com `HttpStatus` embutido**, tratada centralmente em `ApiExceptionHandler` (`@RestControllerAdvice`). Nenhum controller decide status HTTP diretamente — mantém a camada de controller fina.

## 4. Mapa de classes/arquivos

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `security/JwtService` | Gerar e validar tokens JWT | `app.security.jwt-secret` |
| `security/JwtAuthenticationFilter` | Extrair o token do header e popular o `SecurityContext` | `JwtService`, `CustomUserDetailsService` |
| `security/CustomUserDetailsService` | Carregar `User` por email para o Spring Security | `UserRepository` |
| `config/SecurityConfig` | Cadeia de filtros, CORS, rotas públicas/privadas | `JwtAuthenticationFilter` |
| `config/InitialUserSeeder` | Criar o usuário inicial no boot (`INITIAL_USER_*`) | `UserRepository`, `PasswordEncoder` |
| `service/AuthService` | Registro e login — única classe que emite token | `UserRepository`, `JwtService`, `AuthenticationManager` |
| `service/ProfileService` | Leitura e edição do perfil do usuário autenticado | `UserRepository` |
| `controller/AuthController` | Rotas `/api/auth/*` | `AuthService` |
| `controller/ProfileController` | Rotas `/api/profile` | `ProfileService` |

## 5. Fluxo de uma requisição

`POST /api/auth/login`:

1. `AuthController.login` recebe `LoginRequest`, valida com Bean Validation.
2. `AuthService.login` chama `AuthenticationManager.authenticate` (que usa `CustomUserDetailsService` + `PasswordEncoder` por baixo).
3. Se a senha bater, busca o `User`, gera o token com `JwtService.generateToken`.
4. Retorna `AuthResponse` (token + `UserResponse` via `UserMapper`).

Requisição autenticada subsequente (`GET /api/profile`):

1. `JwtAuthenticationFilter` intercepta antes do `UsernamePasswordAuthenticationFilter`, valida o token, popula o `SecurityContext`.
2. `ProfileController.getProfile` lê o email autenticado via `@AuthenticationPrincipal`.
3. `ProfileService.getProfile` busca o `User` e retorna o DTO.

## 6. Trechos comentados

```java
// security/JwtService.java — por que verificar com verifyWith em vez de setSigningKey:
// jjwt 0.12 exige o tipo exato da chave (SecretKey) no parser; assinar e verificar
// com a mesma instância de Key evita erro de algoritmo incompatível em runtime.
public boolean isValid(String token) {
    try {
        Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build().parseSignedClaims(token);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

```yaml
# application.yml — ddl-auto=validate nunca cria/altera tabelas.
# Se a entidade e a migration divergirem, o boot falha na inicialização
# do EntityManagerFactory, não silenciosamente em produção.
jpa:
  hibernate:
    ddl-auto: validate
```

## 7. Como testar manualmente

```sh
docker compose -f docker-compose.dev.yml up -d postgres minio mailpit
cd backend && cp .env.example .env   # ajustar JWT_SECRET (mín. 32 chars) e a porta do Postgres
./mvnw spring-boot:run
```

```sh
curl -X POST http://localhost:1337/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@oaksd.local","password":"ChangeMe123!"}'
```

Frontend:

```sh
cd frontend && cp .env.example .env
npm install && npm run dev
```

Abrir `http://localhost:5173/login`, entrar com o usuário inicial.

## 8. O que eu aprendi / erros cometidos

- Esta máquina tinha **dois** PostgreSQL nativos ocupando `5432` e `5433` simultaneamente — o erro reportado pelo driver JDBC (`autenticação do tipo senha falhou`) não tinha nada a ver com credenciais; era o cliente conectando no processo errado. Diagnosticado com `Get-NetTCPConnection -LocalPort <porta>` no PowerShell antes de mexer em usuário/senha.
- O `lombok` herdado do `spring-boot-starter-parent` 3.3.4 não compila anotações em JDK 25 (erro `TypeTag :: UNKNOWN`). Fixar `lombok.version` para uma versão recente resolve sem tocar no `java.version` do projeto.
- `Keys.hmacShaKeyFor` do `jjwt` rejeita segredos curtos (`change-me` tem 72 bits; HMAC-SHA precisa de 256 bits). O `.env.example` já vem com um placeholder longo o suficiente para não repetir o erro.
