import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_URL } from './api.config';

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

interface Session {
  token: string;
  email: string;
  /** Data ISO em que o token deixa de valer. */
  expiresAt: string;
}

export class InvalidCredentialsError extends Error {
  constructor() {
    super('E-mail ou senha inválidos.');
  }
}

const STORAGE_KEY = 'agenda-online:session';

/**
 * Login do administrador.
 *
 * O token JWT devolvido pela API fica guardado no navegador até expirar (8h por padrão)
 * ou até o administrador clicar em "Sair".
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(this.restore());

  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly email = computed(() => this.session()?.email ?? null);

  /** Token válido para enviar à API, ou null se não houver sessão (ou se ela expirou). */
  token(): string | null {
    const s = this.session();
    if (!s) return null;
    if (Date.parse(s.expiresAt) <= Date.now()) {
      this.clear();
      return null;
    }
    return s.token;
  }

  async login(email: string, password: string): Promise<void> {
    try {
      const res = await firstValueFrom(
        this.http.post<LoginResponse>(`${API_URL}/auth/login`, { email, password }),
      );
      this.save({ token: res.accessToken, email: email.trim().toLowerCase(), expiresAt: res.expiresAt });
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 401) throw new InvalidCredentialsError();
      throw err;
    }
  }

  logout(): void {
    this.clear();
    void this.router.navigateByUrl('/');
  }

  /** Chamado quando a API recusa o token (expirado ou inválido): volta para o login. */
  sessionExpired(): void {
    const returnUrl = this.router.url;
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { returnUrl } });
  }

  private save(session: Session): void {
    this.session.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // localStorage indisponível — a sessão vale só enquanto a página estiver aberta.
    }
  }

  private clear(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignora
    }
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as Session;
      if (!s.token || Date.parse(s.expiresAt) <= Date.now()) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return s;
    } catch {
      return null;
    }
  }
}
