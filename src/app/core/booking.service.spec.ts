import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Booking, SlotTakenError, meetingStatus } from './booking.model';
import { BookingService } from './booking.service';

/** Deixa as Promises pendentes (firstValueFrom/await) terminarem. */
const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('BookingService', () => {
  const input = { date: '2030-01-07', time: '09:00', name: 'Ana', email: 'ana@x.com', topic: '' };
  const saved: Booking = {
    ...input,
    id: 'abc',
    createdAt: '2026-01-01T00:00:00Z',
    startsAt: '2030-01-07T12:00:00Z',
    endsAt: '2030-01-07T12:30:00Z',
    completedAt: null,
  };

  let service: BookingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads busy slots on start', async () => {
    expect(service.busyLoading()).toBe(true);
    http.expectOne('/api/slots/busy').flush([{ date: '2030-01-07', time: '09:00' }]);
    await settle();

    expect(service.busyLoading()).toBe(false);
    expect(service.isBooked('2030-01-07', '09:00')).toBe(true);
    expect(service.bookedDates().has('2030-01-07')).toBe(true);
    expect(service.bookedTimes('2030-01-07')).toEqual(new Set(['09:00']));
  });

  it('adds a booking and marks the slot as taken', async () => {
    http.expectOne('/api/slots/busy').flush([]);
    await settle();

    const promise = service.add(input);
    const req = http.expectOne('/api/bookings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    req.flush(saved, { status: 201, statusText: 'Created' });

    await expect(promise).resolves.toEqual(saved);
    expect(service.isBooked('2030-01-07', '09:00')).toBe(true);
  });

  it('turns a 409 into SlotTakenError and refreshes busy slots', async () => {
    http.expectOne('/api/slots/busy').flush([]);
    await settle();

    const promise = service.add(input);
    http
      .expectOne('/api/bookings')
      .flush({ error: 'taken' }, { status: 409, statusText: 'Conflict' });

    await expect(promise).rejects.toBeInstanceOf(SlotTakenError);
    http.expectOne('/api/slots/busy').flush([{ date: '2030-01-07', time: '09:00' }]);
    await settle();
    expect(service.isBooked('2030-01-07', '09:00')).toBe(true);
  });

  it('cancels a booking', async () => {
    http.expectOne('/api/slots/busy').flush([{ date: '2030-01-07', time: '09:00' }]);
    await settle();

    const load = service.loadBookings();
    http.expectOne('/api/admin/bookings').flush([saved]);
    await load;
    expect(service.bookings()).toHaveLength(1);

    const cancel = service.cancel('abc');
    const req = http.expectOne('/api/admin/bookings/abc');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await cancel;

    expect(service.bookings()).toHaveLength(0);
    expect(service.isBooked('2030-01-07', '09:00')).toBe(false);
  });

  it('marks a booking as done', async () => {
    http.expectOne('/api/slots/busy').flush([]);
    const load = service.loadBookings();
    http.expectOne('/api/admin/bookings').flush([saved]);
    await load;

    const done = service.complete('abc');
    const req = http.expectOne('/api/admin/bookings/abc/complete');
    expect(req.request.method).toBe('POST');
    req.flush({ ...saved, completedAt: '2030-01-07T12:10:00Z' });
    await done;

    expect(service.bookings()[0].completedAt).toBe('2030-01-07T12:10:00Z');
  });

  it('computes the meeting status from the clock', () => {
    http.expectOne('/api/slots/busy').flush([]);
    const at = (iso: string) => new Date(iso);
    expect(meetingStatus(saved, at('2030-01-07T11:59:00Z'))).toBe('open');
    expect(meetingStatus(saved, at('2030-01-07T12:00:00Z'))).toBe('in-progress');
    expect(meetingStatus(saved, at('2030-01-07T12:30:00Z'))).toBe('done');
    expect(meetingStatus({ ...saved, completedAt: '2030-01-01T00:00:00Z' }, at('2030-01-06T00:00:00Z'))).toBe('done');
  });

  it('reports when the API is unreachable', async () => {
    http.expectOne('/api/slots/busy').error(new ProgressEvent('error'));
    await settle();
    expect(service.loadError()).not.toBeNull();
    expect(service.busyLoading()).toBe(false);
  });
});
