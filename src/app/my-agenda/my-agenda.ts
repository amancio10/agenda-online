import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Booking } from '../core/booking.model';
import { BookingService } from '../core/booking.service';
import { formatLongDate, formatWeekday, fromIsoDate, toIsoDate } from '../core/date-utils';

interface DayGroup {
  date: string;
  label: string;
  bookings: Booking[];
}

@Component({
  selector: 'app-my-agenda',
  imports: [RouterLink],
  templateUrl: './my-agenda.html',
  styleUrl: './my-agenda.css',
})
export class MyAgenda {
  private readonly bookingService = inject(BookingService);

  private readonly todayIso = toIsoDate(new Date());

  protected readonly upcoming = computed(() =>
    this.group(this.bookingService.bookings().filter((b) => b.date >= this.todayIso)),
  );

  protected readonly past = computed(() =>
    this.group(this.bookingService.bookings().filter((b) => b.date < this.todayIso)).reverse(),
  );

  protected cancel(booking: Booking): void {
    const ok = confirm(`Cancelar a reunião com ${booking.name} às ${booking.time}?`);
    if (ok) this.bookingService.cancel(booking.id);
  }

  private group(bookings: Booking[]): DayGroup[] {
    const groups = new Map<string, Booking[]>();
    for (const b of bookings) groups.set(b.date, [...(groups.get(b.date) ?? []), b]);
    return [...groups].map(([date, list]) => {
      const d = fromIsoDate(date);
      return { date, label: `${formatWeekday(d)}, ${formatLongDate(d)}`, bookings: list };
    });
  }
}
