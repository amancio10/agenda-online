import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { adminGuard } from './admin.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService, InvalidCredentialsError } from './auth.service';

const STORAGE_KEY = 'agenda-online:session';
const inOneHour = () => new Date(Date.now() + 3_600_000).toISOString();

describe('login do administrador', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;

  function setup(): void {
    TestBed.configureTestingModule({
      providers: [
        // Rota curinga: o logout/sessão expirada navega para / e /login.
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  }

  async function login(): Promise<void> {
    const p = auth.login('Admin@Example.com', 'senha-certa');
    http.expectOne('/api/auth/login').flush({ accessToken: 'tok', tokenType: 'Bearer', expiresAt: inOneHour() });
    await p;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    setup();
  });

  afterEach(() => http.verify());

  it('logs in, keeps the session and logs out', async () => {
    expect(auth.isLoggedIn()).toBe(false);
    await login();

    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.email()).toBe('admin@example.com');
    expect(auth.token()).toBe('tok');
    expect(localStorage.getItem(STORAGE_KEY)).toContain('tok');

    auth.logout();
    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('turns a 401 into InvalidCredentialsError', async () => {
    const p = auth.login('admin@example.com', 'errada');
    http.expectOne('/api/auth/login').flush({ error: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await expect(p).rejects.toBeInstanceOf(InvalidCredentialsError);
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('restores a valid session and drops an expired one', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 't1', email: 'a@b.com', expiresAt: inOneHour() }));
    TestBed.resetTestingModule();
    setup();
    expect(auth.token()).toBe('t1');

    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 't2', email: 'a@b.com', expiresAt: '2000-01-01T00:00:00Z' }));
    TestBed.resetTestingModule();
    setup();
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('sends the token only to admin routes', async () => {
    await login();

    void firstValueFrom(client.get('/api/admin/bookings'));
    expect(http.expectOne('/api/admin/bookings').request.headers.get('Authorization')).toBe('Bearer tok');

    void firstValueFrom(client.get('/api/slots/busy'));
    expect(http.expectOne('/api/slots/busy').request.headers.has('Authorization')).toBe(false);
  });

  it('ends the session when the API rejects the token', async () => {
    await login();
    const p = firstValueFrom(client.get('/api/admin/bookings'));
    http.expectOne('/api/admin/bookings').flush(null, { status: 401, statusText: 'Unauthorized' });
    await expect(p).rejects.toBeTruthy();
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('guard sends visitors to /login and lets the admin in', async () => {
    const run = () =>
      TestBed.runInInjectionContext(() => adminGuard({} as never, { url: '/minha-agenda' } as never));

    const denied = run() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(denied)).toBe('/login?returnUrl=%2Fminha-agenda');

    await login();
    expect(run()).toBe(true);
  });
});
