import { TestBed } from '@angular/core/testing';
import { SlotTakenError } from './booking.model';
import { BookingService } from './booking.service';

describe('BookingService', () => {
  const input = { date: '2030-01-07', time: '09:00', name: 'Ana', email: 'ana@x.com', topic: '' };

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('adds a booking and marks the date and time as taken', () => {
    const service = TestBed.inject(BookingService);
    service.add(input);

    expect(service.isBooked('2030-01-07', '09:00')).toBe(true);
    expect(service.bookedDates().has('2030-01-07')).toBe(true);
    expect(service.bookedTimes('2030-01-07')).toEqual(new Set(['09:00']));
  });

  it('refuses to double-book the same slot', () => {
    const service = TestBed.inject(BookingService);
    service.add(input);
    expect(() => service.add({ ...input, name: 'Bia' })).toThrow(SlotTakenError);
  });

  it('persists bookings across instances', () => {
    TestBed.inject(BookingService).add(input);
    TestBed.resetTestingModule();
    expect(TestBed.inject(BookingService).bookings()).toHaveLength(1);
  });

  it('cancels a booking', () => {
    const service = TestBed.inject(BookingService);
    const booking = service.add(input);
    service.cancel(booking.id);
    expect(service.bookings()).toHaveLength(0);
    expect(service.isBooked('2030-01-07', '09:00')).toBe(false);
  });
});
