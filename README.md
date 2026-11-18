# Agenda Online

Agenda pessoal para agendar reuniões: o visitante escolhe um dia e um horário livre no calendário, informa nome e e-mail, e a reunião fica marcada.

Feito com Angular.

## Funcionalidades

- Calendário mensal com dias disponíveis destacados e dias com reuniões marcados com um ponto
- Horários ocupados ficam bloqueados (sem agendamento duplo)
- Cartão de perfil ao lado do calendário
- Página **Minha agenda** (`/minha-agenda`) com as próximas reuniões e opção de cancelar

## Rodando localmente

```bash
npm install
npm start
```

Abra `http://localhost:4200/`.

## Configuração

- Disponibilidade (dias da semana, horários, duração, janela de agendamento): `src/app/core/schedule.config.ts`
- Dados do perfil: `src/app/core/profile.config.ts`
- Foto do perfil: `public/perfil.jpg`

## Observação

Os agendamentos são salvos no `localStorage` do navegador, ou seja, ficam apenas no dispositivo de quem agendou. Para compartilhar a agenda entre visitantes é preciso um backend (ex.: Firebase ou Supabase) — basta trocar a persistência em `src/app/core/booking.service.ts`.

## Testes e build

```bash
npm test
npm run build
```
