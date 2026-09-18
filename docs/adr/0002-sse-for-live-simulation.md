# ADR 0002 — SSE para simulação ao vivo

## Contexto

A Fase 4 precisa mostrar uma simulação rodando em tempo real: gráficos que crescem tick a tick, sem o usuário apertar "atualizar". Três opções concretas: WebSocket, long-polling e Server-Sent Events (SSE).

## Decisão

Usar SSE (`SseEmitter` do Spring MVC) em vez de WebSocket. O fluxo é unidirecional — o servidor empurra ticks e eventos; o cliente nunca manda dados de volta pelo mesmo canal (`stop` é um `POST` HTTP comum, não uma mensagem no socket). SSE é HTTP puro: reconecta nativamente no browser (`EventSource`), passa por proxy/load balancer sem configuração especial, e não exige gerenciar handshake, ping/pong ou fechamento como WebSocket exige.

## Consequências

- `EventSource` não permite header `Authorization` customizado. O JWT trafega como query parameter (`?token=...`) só nessa rota — `JwtAuthenticationFilter` aceita o token do header (padrão) ou do parâmetro `token` como fallback. Risco aceito: o token já é de curta duração (`JWT_EXPIRATION_MS`) e a URL não é logada em nenhum sistema externo neste projeto.
- Cada stream ativo ocupa uma thread do `ExecutorService` do `LiveSimulationService` do início ao fim da simulação (ou até `stop`). Para o volume de uso deste estudo (uma pessoa, poucas simulações simultâneas) isso é aceitável; não escala para milhares de streams concorrentes sem repensar para um modelo reativo.
- `stop` não interrompe a thread (`Thread.interrupt`) — só marca uma flag que o loop confere entre ticks. Um tick já em andamento sempre termina e é persistido; a simulação para no próximo ponto seguro, nunca no meio de uma escrita no banco.
- Sem WebSocket, o cliente nunca envia comandos pelo mesmo canal do stream — controle (start/stop/velocidade) é sempre HTTP request/response comum, mais simples de testar com `curl` do que framing de WebSocket.
