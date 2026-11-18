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
}

export type NewBooking = Omit<Booking, 'id' | 'createdAt'>;

export class SlotTakenError extends Error {
  constructor() {
    super('Este horário já está reservado.');
  }
}
