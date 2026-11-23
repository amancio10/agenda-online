-- =====================================================================
-- 02 - Cria as tabelas da Agenda Online (PostgreSQL / Neon)
-- =====================================================================
-- Onde rodar: Neon > SQL Editor, com o banco "neondb" (padrão do Neon) selecionado.
-- Pode ser executado mais de uma vez sem erro (IF NOT EXISTS).
--
-- As colunas precisam bater com as entidades JPA (Booking e AdminUser):
-- a API usa ddl-auto=validate e não sobe se algo estiver diferente.
-- =====================================================================

BEGIN;

-- Reuniões marcadas pelos visitantes.
CREATE TABLE IF NOT EXISTS booking (
    id           UUID         PRIMARY KEY,
    booking_date DATE         NOT NULL,
    booking_time TIME         NOT NULL,
    name         VARCHAR(80)  NOT NULL,
    email        VARCHAR(254) NOT NULL,
    topic        VARCHAR(500) NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    -- Impede que o mesmo horário seja reservado duas vezes, mesmo com cliques simultâneos.
    -- O índice criado por esta restrição também acelera a busca por data.
    CONSTRAINT uq_booking_slot UNIQUE (booking_date, booking_time)
);

COMMENT ON TABLE  booking              IS 'Reuniões marcadas pelos visitantes.';
COMMENT ON COLUMN booking.booking_date IS 'Data da reunião (sem fuso).';
COMMENT ON COLUMN booking.booking_time IS 'Horário de início, no fuso da agenda (America/Sao_Paulo).';
COMMENT ON COLUMN booking.completed_at IS 'Quando o administrador marcou como realizada (NULL = não marcou).';

-- Administradores que podem fazer login e gerenciar a agenda.
CREATE TABLE IF NOT EXISTS admin_user (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_admin_user_email UNIQUE (email)
);

COMMENT ON TABLE  admin_user               IS 'Administradores da agenda.';
COMMENT ON COLUMN admin_user.email         IS 'Sempre em minúsculas.';
COMMENT ON COLUMN admin_user.password_hash IS 'Hash BCrypt da senha (nunca a senha em texto).';

COMMIT;

-- ---------------------------------------------------------------------
-- O primeiro administrador NÃO é criado aqui, para a senha não ficar
-- neste arquivo. Preencha ADMIN_EMAIL e ADMIN_PASSWORD em
-- backend/.env.local: ao iniciar, a API cria o administrador
-- (com a senha já em hash) se a tabela admin_user estiver vazia.
-- ---------------------------------------------------------------------

-- Conferência: deve listar admin_user e booking.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
