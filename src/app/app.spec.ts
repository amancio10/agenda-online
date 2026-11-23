import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows "Entrar" to visitors and hides "Minha agenda"', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav')?.textContent ?? '';
    expect(nav).toContain('Entrar');
    expect(nav).not.toContain('Minha agenda');
    expect(nav).not.toContain('Sair');
  });

  it('shows "Minha agenda" and "Sair" to a logged-in admin', async () => {
    const expiresAt = new Date(Date.now() + 3_600_000).toISOString();
    localStorage.setItem(
      'agenda-online:session',
      JSON.stringify({ token: 't', email: 'admin@example.com', expiresAt }),
    );
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav')?.textContent ?? '';
    expect(nav).toContain('Minha agenda');
    expect(nav).toContain('Sair');
    expect(nav).not.toContain('Entrar');
  });
});
