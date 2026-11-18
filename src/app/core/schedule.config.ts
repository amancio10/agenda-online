/**
 * Configuração da sua disponibilidade.
 * Ajuste aqui os dias da semana, horários e a janela de agendamento.
 */
export const SCHEDULE = {
  /** Dias da semana em que você atende (0 = domingo ... 6 = sábado). */
  workingDays: [1, 2, 3, 4, 5],
  /** Horários de início das reuniões, no formato HH:mm. */
  slots: ['09:00', '10:30', '12:30', '14:30', '15:00', '16:30'],
  /** Duração de cada reunião, em minutos. */
  meetingMinutes: 30,
  /** Até quantos dias à frente é possível agendar. */
  daysAhead: 60,
} as const;
