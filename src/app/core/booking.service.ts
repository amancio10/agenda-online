import { Injectable, computed, signal } from '@angular/core';
import { Booking, NewBooking, SlotTakenError } from './booking.model';

const STORAGE_KEY = 'agenda-online:bookings';

/**
 * Guarda os agendamentos.
 *
 * Hoje os dados ficam no localStorage do navegador. Para que visitantes de
 * outros computadores vejam os horários ocupados, troque load/persist por
 * chamadas a uma API ou banco (Firebase, Supabase, etc.) — o resto do app
 * não precisa mudar.
 */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly _bookings = signal<Booking[]>(this.load());

  /** Todos os agendamentos, ordenados por data e horário. */
  readonly bookings = computed(() =>
    [...this._bookings()].sort((a, b) =>
      `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`),
    ),
  );

  /** Datas ('YYYY-MM-DD') que têm pelo menos uma reunião. */
  readonly bookedDates = computed(() => new Set(this._bookings().map((b) => b.date)));

  constructor() {
    // Mantém abas abertas sincronizadas.
    globalThis.addEventListener?.('storage', (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) this._bookings.set(this.load());
    });
  }

  bookedTimes(date: string): Set<string> {
    return new Set(this._bookings().filter((b) => b.date === date).map((b) => b.time));
  }

  isBooked(date: string, time: string): boolean {
    return this._bookings().some((b) => b.date === date && b.time === time);
  }

  add(input: NewBooking): Booking {
    // Relê o storage para evitar reservar um horário pego em outra aba.
    this._bookings.set(this.load());
    if (this.isBooked(input.date, input.time)) throw new SlotTakenError();

    const booking: Booking = {
      ...input,
      id: crypto.randomUUID(),
      createdAt: new Date().toISOString(),
    };
    this._bookings.update((list) => [...list, booking]);
    this.persist();
    return booking;
  }

  cancel(id: string): void {
    this._bookings.update((list) => list.filter((b) => b.id !== id));
    this.persist();
  }

  private load(): Booking[] {
    try {
      const raw = globalThis.localStorage?.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as Booking[]) : [];
    } catch {
      return [];
    }
  }

  private persist(): void {
    globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(this._bookings()));
  }
}
