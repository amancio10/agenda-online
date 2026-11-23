-- =====================================================================
-- 03 - Adiciona o status "realizada" às reuniões
-- =====================================================================
-- Onde rodar: Neon > SQL Editor, banco "neondb".
-- Só é necessário se a tabela booking foi criada antes desta coluna existir
-- no script 02. Pode ser executado mais de uma vez sem erro.
--
-- Rode ANTES de subir a nova versão da API (ddl-auto=validate).
-- =====================================================================

ALTER TABLE booking ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

COMMENT ON COLUMN booking.completed_at IS 'Quando o administrador marcou como realizada (NULL = não marcou).';

-- Conferência: deve listar completed_at.
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'booking'
ORDER BY ordinal_position;
