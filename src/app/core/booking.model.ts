export interface Booking {
  id: string;
  /** Data no formato 'YYYY-MM-DD'. */
  date: string;
  /** Horário no formato 'HH:mm'. */
  time: string;
  name: string;
  email: string;
  topic: string;
  createdAt: string;
  /** Início e fim da reunião (ISO, UTC). */
  startsAt: string;
  endsAt: string;
  /** Quando foi marcada como realizada pelo administrador (null = não marcada). */
  completedAt: string | null;
}

/** open = em aberto, in-progress = em andamento, done = realizada. */
export type MeetingStatus = 'open' | 'in-progress' | 'done';

export const STATUS_LABEL: Record<MeetingStatus, string> = {
  open: 'Em aberto',
  'in-progress': 'Em andamento',
  done: 'Realizada',
};

/**
 * Status da reunião neste instante: realizada se foi finalizada pelo administrador
 * ou se o horário de término já passou; em andamento entre o início e o fim.
 */
export function meetingStatus(b: Booking, now: Date = new Date()): MeetingStatus {
  const t = now.getTime();
  if (b.completedAt || t >= Date.parse(b.endsAt)) return 'done';
  if (t >= Date.parse(b.startsAt)) return 'in-progress';
  return 'open';
}

/** Horário ocupado, sem dados pessoais (visão pública). */
export type BusySlot = Pick<Booking, 'date' | 'time'>;

export type NewBooking = Pick<Booking, 'date' | 'time' | 'name' | 'email' | 'topic'>;

export class SlotTakenError extends Error {
  constructor() {
    super('Este horário já está reservado.');
  }
}
