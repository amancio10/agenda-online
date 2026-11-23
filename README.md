# Agenda Online

Aplicação full stack de agendamento de reuniões. O visitante escolhe um dia e um horário livre num calendário e marca uma reunião. O administrador faz login, acompanha as reuniões em abertas, em andamento e realizadas, e recebe um e-mail a cada novo agendamento.

Front-end em **Angular**, API REST em **Java / Spring Boot** e banco **PostgreSQL serverless (Neon)**.

---

## Sumário

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Arquitetura](#arquitetura)
- [Como cada parte foi feita](#como-cada-parte-foi-feita)
- [Rotas da API](#rotas-da-api)
- [Estrutura de pastas](#estrutura-de-pastas)
- [Rodando localmente](#rodando-localmente)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Testes](#testes)
- [Publicação](#publicação)
- [Autor](#autor)

---

## Funcionalidades

**Visitante**
- Calendário mensal: os dias com horários livres ficam destacados.
- Lista de horários do dia. Os já reservados e os que já passaram ficam bloqueados.
- Formulário de agendamento (nome, e-mail e assunto) com validação.
- Cartão de perfil ao lado do calendário.
- Modo claro e escuro. A escolha fica salva e a página não pisca ao carregar.

**Administrador**
- Login com e-mail e senha. A sessão usa um token JWT com prazo de validade.
- Página **Minha agenda** com duas abas:
  - **Próximas:** reuniões *em aberto* e *em andamento*, com as ações **Finalizar** e **Cancelar**.
  - **Realizadas:** reuniões finalizadas manualmente e as que já passaram do horário de término.
- O status muda sozinho com o relógio (em aberto → em andamento → realizada), sem recarregar a página.
- Aviso por e-mail a cada nova reunião. O "Responder" do e-mail vai direto para o visitante.

---

## Tecnologias

| Camada | Tecnologias |
|---|---|
| **Front-end** | Angular 22 (componentes standalone, Signals, novo control flow `@if`/`@for`), TypeScript 6, RxJS, Reactive Forms, CSS com variáveis (tema claro/escuro) |
| **Back-end** | Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA / Hibernate, Spring Security 7, OAuth2 Resource Server (JWT), Bean Validation, Spring Mail, Maven |
| **Banco de dados** | PostgreSQL no Neon (serverless), com *connection pooling* |
| **Testes** | Vitest + `HttpTestingController` no front; JUnit 5, MockMvc, Mockito e H2 (modo PostgreSQL) no back |
| **Segurança** | BCrypt para senhas, JWT HS256, CORS restrito, segredos fora do repositório |

---

## Arquitetura

```mermaid
flowchart LR
    V[Visitante] -->|navegador| A[Angular SPA]
    ADM[Administrador] -->|login| A
    A -->|HTTP / JSON<br/>Bearer JWT nas rotas de admin| API[API Spring Boot]
    API -->|JPA / JDBC + SSL| DB[(PostgreSQL<br/>Neon)]
    API -->|SMTP + STARTTLS| MAIL[Servidor de e-mail]
    MAIL -->|aviso de nova reunião| ADM
```

- O front nunca fala direto com o banco. Todo acesso passa pela API, que valida, autoriza e grava.
- Visitantes só recebem **horários ocupados**, nunca nomes ou e-mails de outras pessoas.
- Em desenvolvimento, o Angular usa um *proxy* (`proxy.conf.json`) que encaminha `/api` para `localhost:8080`. Assim o front e a API parecem estar na mesma origem.

---

## Como cada parte foi feita

### Front-end (Angular)

- **Componentes standalone e Signals.** O estado da tela (dia selecionado, horários, sessão, lista de reuniões) fica em `signal` e `computed`, sem NgModules e sem bibliotecas de estado. A interface atualiza só o que mudou.
- **Calendário próprio**, sem biblioteca externa. A disponibilidade (dias da semana, horários, duração e quantos dias à frente) fica em um só arquivo: [`schedule.config.ts`](src/app/core/schedule.config.ts). Um relógio interno atualiza a cada minuto e esconde os horários que já passaram.
- **Camada de serviços.** O [`BookingService`](src/app/core/booking.service.ts) concentra as chamadas HTTP e mantém o cache das reuniões em Signals. Um erro `409` da API vira um `SlotTakenError`, para a tela avisar que o horário acabou de ser reservado por outra pessoa.
- **Autenticação no cliente:**
  - [`AuthService`](src/app/core/auth.service.ts) guarda o token e a data de expiração, e restaura a sessão ao recarregar a página.
  - [`authInterceptor`](src/app/core/auth.interceptor.ts) é um interceptor funcional que envia o `Authorization: Bearer` **apenas** para as rotas de administrador. Se a API responder `401`, a sessão é encerrada e o usuário volta ao login.
  - [`adminGuard`](src/app/core/admin.guard.ts) protege `/minha-agenda` e redireciona para `/login?returnUrl=...`. O `returnUrl` é validado para evitar *open redirect*.
- **Status das reuniões.** A função pura `meetingStatus()` em [`booking.model.ts`](src/app/core/booking.model.ts) calcula o status a partir dos instantes de início e fim enviados pela API (em UTC). Isso evita divergência de fuso horário entre o navegador e o servidor.
- **Tema claro/escuro.** As cores ficam em variáveis CSS (`:root` e `:root.dark`). O [`ThemeService`](src/app/core/theme.service.ts) salva a preferência, e um pequeno script no `index.html` aplica o tema antes do Angular carregar, para a página não piscar.
- **Acessibilidade.** As abas usam `role="tablist"`/`role="tab"` com `aria-selected`, as mensagens de erro usam `role="alert"`, e os botões ficam desabilitados enquanto uma ação está em andamento.

### Back-end (Spring Boot)

O código fica em `backend/` e está organizado por domínio (`auth`, `booking`, `config`), não por tipo de classe.

- **Camadas:** Controller → Service → Repository (Spring Data JPA). A entrada e a saída da API usam *records* Java (DTOs), separados das entidades JPA. Assim nenhuma coluna do banco é exposta sem querer.
- **Controllers separados por público:** `VisitorBookingController` (rotas públicas) e `AdminBookingController` (rotas que exigem login). Isso deixa claro o que cada perfil pode fazer.
- **Validação** com Bean Validation (`@NotBlank`, `@Email`, `@Size`) e regras de negócio no serviço, como não aceitar agendamento no passado.
- **Tratamento de erros centralizado** no [`ApiExceptionHandler`](backend/src/main/java/com/agendaonline/backend/config/ApiExceptionHandler.java). Cada exceção vira uma resposta JSON padronizada:

  | Situação | Status |
  |---|---|
  | Horário já reservado | `409` |
  | Login inválido | `401` |
  | Reunião não encontrada | `404` |
  | Dados inválidos (com o erro de cada campo) | `400` |

- **Sem agendamento duplo, mesmo com cliques simultâneos.** O banco tem uma restrição `UNIQUE (booking_date, booking_time)`. O serviço usa `saveAndFlush` e converte a violação dessa restrição em `409`. A garantia fica no banco, não só no código.
- **Status "realizada".** A coluna `completed_at` guarda quando o administrador finalizou a reunião. O status (`OPEN`, `IN_PROGRESS`, `DONE`) é **calculado** a partir do horário e da duração configurada. Por isso uma reunião vira "realizada" sozinha ao passar do horário, sem precisar de tarefa agendada (*cron*).
- **E-mail de aviso assíncrono.** O [`BookingNotifier`](backend/src/main/java/com/agendaonline/backend/booking/BookingNotifier.java) envia o aviso com `@Async`. O visitante não espera o SMTP, e uma falha no e-mail só é registrada no log, sem desfazer o agendamento. O recurso fica desligado enquanto as variáveis de e-mail estiverem vazias. Quebras de linha vindas do visitante são removidas do assunto, para evitar injeção de cabeçalhos.
- **Configuração por variáveis de ambiente.** O [`DotEnv`](backend/src/main/java/com/agendaonline/backend/config/DotEnv.java) lê o `backend/.env.local` em desenvolvimento e converte a `DATABASE_URL` do Neon (`postgresql://usuario:senha@host/db`) para o formato JDBC. Em produção, as variáveis vêm da hospedagem.

### Segurança

- **Sessão sem estado (stateless) com JWT.** No login, a API confere a senha (hash **BCrypt**) e emite um token **HS256** com emissor, validade (padrão 8h) e o papel `ADMIN`. As rotas são protegidas pelo OAuth2 Resource Server do Spring Security.
- **Regras de acesso explícitas** em [`SecurityConfig`](backend/src/main/java/com/agendaonline/backend/config/SecurityConfig.java):
  - Públicas: listar horários ocupados, criar agendamento e fazer login.
  - `/api/admin/**`: apenas `ADMIN`.
  - Qualquer outra rota: **negada** (`denyAll`).
- **Proteção contra enumeração de usuários.** Quando o e-mail não existe, a API compara a senha com um hash fictício. Assim o tempo de resposta é o mesmo para "e-mail não existe" e "senha errada".
- **CORS** liberado só para as origens configuradas.
- **Primeiro administrador.** O [`AdminBootstrap`](backend/src/main/java/com/agendaonline/backend/auth/AdminBootstrap.java) cria o admin a partir das variáveis de ambiente **apenas se a tabela estiver vazia**, já com a senha em hash. Nenhuma senha fica em script SQL.
- **Repositório público sem segredos.** Arquivos `.env*` (exceto o `.env.example`), chaves e credenciais estão no `.gitignore`.

### Banco de dados (PostgreSQL / Neon)

- As tabelas são criadas por scripts SQL versionados em [`backend/db/`](backend/db/), não pela aplicação. A API usa `ddl-auto=validate`: se o banco não bater com as entidades, ela não sobe. Assim erros de schema aparecem logo na inicialização.

  | Script | Para quê |
  |---|---|
  | `02-create-tables.sql` | Cria `booking` e `admin_user` (pode rodar mais de uma vez) |
  | `03-add-completed-at.sql` | Migração para bancos criados antes da coluna `completed_at` |
  | `01-create-database.sql` | Opcional: cria um banco separado em vez de usar o `neondb` padrão |

- O pool de conexões (HikariCP) é pequeno (máximo 5, mínimo ocioso 0). O Neon já faz *pooling* do lado dele, e o banco serverless pode "dormir" quando não há uso.
- Os testes automatizados usam **H2 em memória no modo PostgreSQL**. Eles não dependem do Neon nem de internet.

---

## Rotas da API

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/api/slots/busy` | público | Horários ocupados a partir de hoje (só data e hora) |
| `POST` | `/api/bookings` | público | Cria um agendamento |
| `POST` | `/api/auth/login` | público | Login do administrador. Devolve o token JWT |
| `GET` | `/api/auth/me` | admin | Dados do administrador logado |
| `GET` | `/api/admin/bookings` | admin | Todas as reuniões, com contato e status |
| `POST` | `/api/admin/bookings/{id}/complete` | admin | Marca a reunião como realizada |
| `DELETE` | `/api/admin/bookings/{id}` | admin | Cancela a reunião e libera o horário |

Exemplo de reunião retornada para o administrador:

```json
{
  "id": "4f1c…",
  "date": "2030-01-07",
  "time": "09:00",
  "name": "Ana",
  "email": "ana@example.com",
  "topic": "Projeto",
  "createdAt": "2029-12-20T14:03:11Z",
  "startsAt": "2030-01-07T12:00:00Z",
  "endsAt": "2030-01-07T12:30:00Z",
  "completedAt": null,
  "status": "OPEN"
}
```

---

## Estrutura de pastas

```text
agenda-online/
├── src/app/                      # Front-end Angular
│   ├── booking-page/             # Calendário + formulário (visitante)
│   ├── my-agenda/                # Abas Próximas / Realizadas (admin)
│   ├── login/                    # Tela de login
│   ├── profile-card/             # Cartão de perfil
│   └── core/                     # Serviços, modelos, interceptor, guard, configs
├── proxy.conf.json               # /api → localhost:8080 em desenvolvimento
└── backend/                      # API Spring Boot
    ├── db/                       # Scripts SQL (criação e migração)
    ├── .env.example              # Modelo das variáveis de ambiente
    └── src/main/java/com/agendaonline/backend/
        ├── auth/                 # Login, JWT, administrador
        ├── booking/              # Agendamentos, status, aviso por e-mail
        └── config/               # Segurança, CORS, erros, leitura do .env
```

---

## Rodando localmente

**Pré-requisitos:** Node.js 22.22+ ou 24.15+, Java 21 e um banco PostgreSQL. O [Neon](https://neon.tech) tem plano gratuito.

1. **Banco.** No SQL Editor do Neon, rode `backend/db/02-create-tables.sql`.

2. **Variáveis da API.** Copie `backend/.env.example` para `backend/.env.local` e preencha (veja a tabela abaixo).

3. **API** (porta 8080). Não precisa de IDE, o Maven Wrapper já vem no projeto:

   ```bash
   cd backend
   ./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
   ```

4. **Front-end** (porta 4200), em outro terminal, na raiz do projeto:

   ```bash
   npm install
   npm start
   ```

5. Abra `http://localhost:4200`. Para entrar como administrador, clique em **Entrar** e use o `ADMIN_EMAIL` e o `ADMIN_PASSWORD` definidos no `.env.local`.

**Personalização:** a disponibilidade fica em [`schedule.config.ts`](src/app/core/schedule.config.ts), os dados do perfil em [`profile.config.ts`](src/app/core/profile.config.ts) e a foto em `public/perfil.jpg`.

---

## Variáveis de ambiente

Ficam em `backend/.env.local`, que é ignorado pelo Git. Em produção, cadastre as mesmas variáveis no painel da hospedagem.

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DATABASE_URL` | sim | Endereço do Neon (`postgresql://…?sslmode=require`) |
| `JWT_SECRET` | sim | Segredo aleatório de no mínimo 32 caracteres para assinar os tokens |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | na 1ª execução | Criam o primeiro administrador (senha com 8+ caracteres) |
| `NOTIFY_EMAIL` | não | Quem recebe o aviso de nova reunião |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | não | Conta SMTP que envia o aviso (no Gmail, use uma *senha de app*) |
| `MAIL_HOST` / `MAIL_PORT` | não | Servidor SMTP (padrão `smtp.gmail.com:587`) |
| `CORS_ALLOWED_ORIGINS` | não | Origens do front permitidas (padrão `http://localhost:4200`) |
| `JWT_TTL` | não | Validade do login (padrão `8h`) |
| `MEETING_MINUTES` | não | Duração da reunião (padrão `30`; mantenha igual ao front) |
| `APP_TIMEZONE` | não | Fuso da agenda (padrão `America/Sao_Paulo`) |

---

## Testes

```bash
# Front-end (Vitest)
npm test

# Back-end (JUnit + MockMvc + H2)
cd backend && ./mvnw test
```

- **Front-end:**
  - Serviço de agendamentos: carregar, criar, tratar o `409`, finalizar, cancelar e cálculo de status.
  - Login e logout, restauração e expiração da sessão.
  - Envio do token só nas rotas de admin e guard da rota protegida.
  - Menu para visitante e para administrador.
- **Back-end:**
  - Testes de ponta a ponta das rotas (HTTP → segurança → JPA): permissões, `400`/`401`/`404`/`409`, login, listagem, finalização, cancelamento e CORS.
  - Testes unitários do aviso por e-mail (com Mockito) e da conversão da `DATABASE_URL`.

---

## Publicação

Arquitetura pensada para hospedagem gratuita:

| Parte | Opções |
|---|---|
| Front-end | Vercel, Netlify ou Cloudflare Pages (`npm run build` → `dist/`) |
| API | Google Cloud Run ou Render (a porta vem da variável `PORT`) |
| Banco | Neon (plano gratuito) |

Na produção, configure `CORS_ALLOWED_ORIGINS` com o endereço do front. Configure também o front para chamar a URL pública da API (`src/app/core/api.config.ts`) ou um *rewrite* `/api` na hospedagem.

---

## Autor

**Amâncio Santos**, desenvolvedor, Análise e Desenvolvimento de Sistemas na Impacta Tecnologia.

- Portfólio: [amancio10.github.io](https://amancio10.github.io)
- LinkedIn: [linkedin.com/in/amancio-santos](https://www.linkedin.com/in/amancio-santos/)
