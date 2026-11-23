-- =====================================================================
-- 01 - Cria o banco de dados da Agenda Online (PostgreSQL / Neon)
-- =====================================================================
-- Onde rodar: Neon > SQL Editor, com o banco "neondb" selecionado.
--
-- CREATE DATABASE não pode rodar dentro de uma transação: execute este
-- comando sozinho. Se der erro de "already exists", o banco já foi criado.
--
-- Depois de criar:
--   1. Troque o banco no seletor do SQL Editor para "agenda_online"
--      e rode o script 02-create-tables.sql.
--   2. Em backend/.env.local, troque o final da DATABASE_URL de
--      "/neondb?..." para "/agenda_online?..." (o restante fica igual).
-- =====================================================================

CREATE DATABASE agenda_online;


-- ---------------------------------------------------------------------
-- Opcional: limpeza do banco "neondb".
-- Nos testes anteriores, a API antiga criou ali as tabelas abaixo.
-- Elas não são mais usadas. Rode só se quiser apagá-las (com "neondb"
-- selecionado). Os dados de teste já tinham sido removidos.
-- ---------------------------------------------------------------------
-- DROP TABLE IF EXISTS booking;
-- DROP TABLE IF EXISTS flyway_schema_history;
