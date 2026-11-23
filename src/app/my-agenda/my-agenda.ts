import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Booking, MeetingStatus, STATUS_LABEL, meetingStatus } from '../core/booking.model';
import { BookingService } from '../core/booking.service';
import { formatLongDate, formatWeekday, fromIsoDate } from '../core/date-utils';

type Tab = 'upcoming' | 'done';

interface Item {
  booking: Booking;
  status: MeetingStatus;
  label: string;
}

interface DayGroup {
  date: string;
  label: string;
  items: Item[];
}

@Component({
  selector: 'app-my-agenda',
  imports: [RouterLink, NgTemplateOutlet],
  templateUrl: './my-agenda.html',
  styleUrl: './my-agenda.css',
})
export class MyAgenda {
  private readonly bookingService = inject(BookingService);

  /** Relógio: atualizado a cada 30 s para o status mudar sozinho (em aberto → em andamento → realizada). */
  private readonly now = signal(new Date());

  protected readonly loadError = this.bookingService.loadError;
  protected readonly loading = this.bookingService.bookingsLoading;
  protected readonly tab = signal<Tab>('upcoming');
  /** Reunião com uma ação em andamento (desabilita os botões dela). */
  protected readonly busyId = signal<string | null>(null);

  constructor() {
    void this.bookingService.loadBookings();
    const timer = setInterval(() => this.now.set(new Date()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  private readonly items = computed<Item[]>(() => {
    const now = this.now();
    return this.bookingService.bookings().map((booking) => {
      const status = meetingStatus(booking, now);
      return { booking, status, label: STATUS_LABEL[status] };
    });
  });

  /** Em aberto e em andamento, da mais próxima para a mais distante. */
  protected readonly upcoming = computed(() => this.group(this.items().filter((i) => i.status !== 'done')));

  /** Realizadas, da mais recente para a mais antiga. */
  protected readonly done = computed(() =>
    this.group(this.items().filter((i) => i.status === 'done').reverse()),
  );

  protected readonly upcomingCount = computed(() => this.count(this.upcoming()));
  protected readonly doneCount = computed(() => this.count(this.done()));

  protected async complete(b: Booking): Promise<void> {
    if (!confirm(`Marcar a reunião com ${b.name} às ${b.time} como realizada?`)) return;
    await this.run(b, () => this.bookingService.complete(b.id), 'finalizar');
  }

  protected async cancel(b: Booking): Promise<void> {
    if (!confirm(`Cancelar a reunião com ${b.name} às ${b.time}?`)) return;
    await this.run(b, () => this.bookingService.cancel(b.id), 'cancelar');
  }

  private async run(b: Booking, action: () => Promise<void>, verb: string): Promise<void> {
    this.busyId.set(b.id);
    try {
      await action();
    } catch {
      alert(`Não foi possível ${verb}. Tente novamente.`);
    } finally {
      this.busyId.set(null);
    }
  }

  private count(groups: DayGroup[]): number {
    return groups.reduce((n, g) => n + g.items.length, 0);
  }

  private group(items: Item[]): DayGroup[] {
    const groups = new Map<string, Item[]>();
    for (const i of items) groups.set(i.booking.date, [...(groups.get(i.booking.date) ?? []), i]);
    return [...groups].map(([date, list]) => {
      const d = fromIsoDate(date);
      return { date, label: `${formatWeekday(d)}, ${formatLongDate(d)}`, items: list };
    });
  }
}
