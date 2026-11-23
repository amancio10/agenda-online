import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Booking, SlotTakenError } from '../core/booking.model';
import { BookingService } from '../core/booking.service';
import {
  addDays,
  atTime,
  formatLongDate,
  formatMonthYear,
  formatWeekday,
  startOfDay,
  toIsoDate,
} from '../core/date-utils';
import { SCHEDULE } from '../core/schedule.config';
import { ProfileCard } from '../profile-card/profile-card';

interface DayCell {
  date: Date | null;
  available: boolean;
  selected: boolean;
  hasBooking: boolean;
  today: boolean;
}

interface Slot {
  time: string;
  booked: boolean;
}

type Step = 'pick' | 'form' | 'done';

@Component({
  selector: 'app-booking-page',
  imports: [ReactiveFormsModule, ProfileCard],
  templateUrl: './booking-page.html',
  styleUrl: './booking-page.css',
})
export class BookingPage {
  private readonly bookingService = inject(BookingService);

  protected readonly weekdayLabels = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB'];
  protected readonly formatWeekday = formatWeekday;
  protected readonly formatLongDate = formatLongDate;

  /** Atualizado a cada minuto para esconder horários que já passaram. */
  private readonly now = signal(new Date());
  private readonly today = computed(() => startOfDay(this.now()));
  private readonly lastDay = computed(() => addDays(this.today(), SCHEDULE.daysAhead));

  protected readonly viewMonth = signal(new Date());
  protected readonly selectedDate = signal<Date | null>(null);
  protected readonly selectedTime = signal<string | null>(null);
  protected readonly step = signal<Step>('pick');
  protected readonly error = signal<string | null>(null);
  protected readonly confirmed = signal<Booking | null>(null);
  protected readonly saving = signal(false);
  protected readonly loadError = this.bookingService.loadError;
  protected readonly loading = this.bookingService.busyLoading;
  /** Liga depois de alguns segundos carregando: provavelmente a API está "acordando". */
  protected readonly slowStart = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    topic: ['', [Validators.maxLength(500)]],
  });

  protected readonly monthLabel = computed(() => formatMonthYear(this.viewMonth()));

  protected readonly canGoPrev = computed(() => {
    const v = this.viewMonth();
    const t = this.today();
    return v.getFullYear() * 12 + v.getMonth() > t.getFullYear() * 12 + t.getMonth();
  });

  protected readonly canGoNext = computed(() => {
    const v = this.viewMonth();
    return new Date(v.getFullYear(), v.getMonth() + 1, 1) <= this.lastDay();
  });

  protected readonly cells = computed<DayCell[]>(() => {
    const view = this.viewMonth();
    const year = view.getFullYear();
    const month = view.getMonth();
    const leading = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const selected = this.selectedDate();
    const bookedDates = this.bookingService.bookedDates();
    const todayIso = toIsoDate(this.today());

    const cells: DayCell[] = Array.from({ length: leading }, () => ({
      date: null,
      available: false,
      selected: false,
      hasBooking: false,
      today: false,
    }));

    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(year, month, day);
      const iso = toIsoDate(date);
      cells.push({
        date,
        available: this.isAvailable(date),
        selected: !!selected && toIsoDate(selected) === iso,
        hasBooking: bookedDates.has(iso),
        today: iso === todayIso,
      });
    }
    return cells;
  });

  protected readonly slots = computed<Slot[]>(() => {
    const date = this.selectedDate();
    return date ? this.slotsFor(date) : [];
  });

  constructor() {
    const first = this.findFirstAvailable();
    if (first) {
      this.selectedDate.set(first);
      this.viewMonth.set(new Date(first.getFullYear(), first.getMonth(), 1));
    }

    const timer = setInterval(() => this.now.set(new Date()), 60_000);
    const slow = setTimeout(() => this.slowStart.set(true), 4_000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      clearTimeout(slow);
    });
  }

  protected prevMonth(): void {
    if (this.canGoPrev()) this.shiftMonth(-1);
  }

  protected nextMonth(): void {
    if (this.canGoNext()) this.shiftMonth(1);
  }

  protected selectDate(date: Date): void {
    this.selectedDate.set(date);
    this.selectedTime.set(null);
    this.error.set(null);
    this.step.set('pick');
  }

  protected toggleTime(time: string): void {
    this.selectedTime.update((current) => (current === time ? null : time));
    this.error.set(null);
  }

  protected goToForm(): void {
    if (this.selectedDate() && this.selectedTime()) this.step.set('form');
  }

  protected back(): void {
    this.step.set('pick');
  }

  protected async confirm(): Promise<void> {
    const date = this.selectedDate();
    const time = this.selectedTime();
    if (!date || !time) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.saving()) return;

    const { name, email, topic } = this.form.getRawValue();
    this.saving.set(true);
    try {
      const booking = await this.bookingService.add({
        date: toIsoDate(date),
        time,
        name: name.trim(),
        email: email.trim(),
        topic: topic.trim(),
      });
      this.confirmed.set(booking);
      this.form.reset();
      this.step.set('done');
    } catch (err) {
      if (err instanceof SlotTakenError) {
        this.error.set('Esse horário acabou de ser reservado. Escolha outro, por favor.');
        this.selectedTime.set(null);
        this.step.set('pick');
      } else {
        this.error.set('Não foi possível concluir o agendamento. Tente novamente.');
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected bookAnother(): void {
    this.confirmed.set(null);
    this.selectedTime.set(null);
    this.step.set('pick');
  }

  protected dayAriaLabel(cell: DayCell): string {
    if (!cell.date) return '';
    const label = formatLongDate(cell.date);
    return cell.available ? label : `${label} (indisponível)`;
  }

  private shiftMonth(delta: number): void {
    this.viewMonth.update((v) => new Date(v.getFullYear(), v.getMonth() + delta, 1));
  }

  private slotsFor(date: Date): Slot[] {
    const booked = this.bookingService.bookedTimes(toIsoDate(date));
    const now = this.now();
    return SCHEDULE.slots
      .filter((time) => atTime(date, time) > now)
      .map((time) => ({ time, booked: booked.has(time) }));
  }

  private isAvailable(date: Date): boolean {
    if (date < this.today() || date > this.lastDay()) return false;
    if (!(SCHEDULE.workingDays as readonly number[]).includes(date.getDay())) return false;
    return this.slotsFor(date).some((slot) => !slot.booked);
  }

  private findFirstAvailable(): Date | null {
    for (let i = 0; i <= SCHEDULE.daysAhead; i++) {
      const date = addDays(this.today(), i);
      if (this.isAvailable(date)) return date;
    }
    return null;
  }
}
