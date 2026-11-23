import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from './api.config';
import { Booking, BusySlot, NewBooking, SlotTakenError } from './booking.model';

/**
 * Agendamentos guardados no banco (Neon) por meio da API Spring Boot.
 *
 * - Visitantes só recebem os horários ocupados (`/slots/busy`), sem nomes ou e-mails.
 * - A lista completa (`/admin/bookings`, exige login) é usada pela tela "Minha agenda".
 */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);

  private readonly _busy = signal<BusySlot[]>([]);
  private readonly _bookings = signal<Booking[]>([]);

  /** Mensagem de erro quando a API não responde (ex.: servidor desligado). */
  readonly loadError = signal<string | null>(null);

  /** Todos os agendamentos, ordenados por data e horário (carregue com `loadBookings`). */
  readonly bookings = computed(() =>
    [...this._bookings()].sort((a, b) =>
      `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`),
    ),
  );

  /** Datas ('YYYY-MM-DD') que têm pelo menos uma reunião. */
  readonly bookedDates = computed(() => new Set(this._busy().map((b) => b.date)));

  constructor() {
    void this.refreshBusy();
  }

  bookedTimes(date: string): Set<string> {
    return new Set(this._busy().filter((b) => b.date === date).map((b) => b.time));
  }

  isBooked(date: string, time: string): boolean {
    return this._busy().some((b) => b.date === date && b.time === time);
  }

  /** Atualiza os horários ocupados (visão pública). */
  async refreshBusy(): Promise<void> {
    try {
      this._busy.set(await firstValueFrom(this.http.get<BusySlot[]>(`${API_URL}/slots/busy`)));
      this.loadError.set(null);
    } catch {
      this.loadError.set('Não foi possível carregar os horários. Tente novamente em instantes.');
    }
  }

  /** Carrega a lista completa de agendamentos (tela "Minha agenda"). */
  async loadBookings(): Promise<void> {
    try {
      this._bookings.set(await firstValueFrom(this.http.get<Booking[]>(`${API_URL}/admin/bookings`)));
      this.loadError.set(null);
    } catch {
      this.loadError.set('Não foi possível carregar a agenda. Tente novamente em instantes.');
    }
  }

  async add(input: NewBooking): Promise<Booking> {
    try {
      const booking = await firstValueFrom(this.http.post<Booking>(`${API_URL}/bookings`, input));
      this._busy.update((list) => [...list, { date: booking.date, time: booking.time }]);
      this._bookings.update((list) => [...list, booking]);
      return booking;
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 409) {
        void this.refreshBusy();
        throw new SlotTakenError();
      }
      throw err;
    }
  }

  /** Marca a reunião como realizada. */
  async complete(id: string): Promise<void> {
    const updated = await firstValueFrom(
      this.http.post<Booking>(`${API_URL}/admin/bookings/${id}/complete`, null),
    );
    this._bookings.update((list) => list.map((b) => (b.id === id ? updated : b)));
  }

  async cancel(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API_URL}/admin/bookings/${id}`));
    const removed = this._bookings().find((b) => b.id === id);
    this._bookings.update((list) => list.filter((b) => b.id !== id));
    if (removed) {
      this._busy.update((list) =>
        list.filter((b) => !(b.date === removed.date && b.time === removed.time)),
      );
    }
  }
}
